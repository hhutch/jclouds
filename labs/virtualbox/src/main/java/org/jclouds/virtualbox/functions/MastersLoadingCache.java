/**
 * Licensed to jclouds, Inc. (jclouds) under one or more
 * contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  jclouds licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jclouds.virtualbox.functions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.jclouds.virtualbox.config.VirtualBoxConstants.VIRTUALBOX_DEFAULT_DIR;
import static org.jclouds.virtualbox.config.VirtualBoxConstants.VIRTUALBOX_IMAGE_PREFIX;
import static org.jclouds.virtualbox.config.VirtualBoxConstants.VIRTUALBOX_INSTALLATION_KEY_SEQUENCE;
import static org.jclouds.virtualbox.config.VirtualBoxConstants.VIRTUALBOX_NODE_NAME_SEPARATOR;
import static org.jclouds.virtualbox.config.VirtualBoxConstants.VIRTUALBOX_PRECONFIGURATION_URL;
import static org.jclouds.virtualbox.config.VirtualBoxConstants.VIRTUALBOX_WORKINGDIR;
import static org.jclouds.virtualbox.util.MachineUtils.machineNotFoundException;

import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.jclouds.Constants;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.reference.ComputeServiceConstants;
import org.jclouds.logging.Logger;
import org.jclouds.virtualbox.domain.HardDisk;
import org.jclouds.virtualbox.domain.IsoSpec;
import org.jclouds.virtualbox.domain.Master;
import org.jclouds.virtualbox.domain.MasterSpec;
import org.jclouds.virtualbox.domain.NetworkAdapter;
import org.jclouds.virtualbox.domain.NetworkInterfaceCard;
import org.jclouds.virtualbox.domain.NetworkSpec;
import org.jclouds.virtualbox.domain.StorageController;
import org.jclouds.virtualbox.domain.VmSpec;
import org.jclouds.virtualbox.domain.YamlImage;
import org.jclouds.virtualbox.functions.admin.PreseedCfgServer;
import org.virtualbox_4_1.CleanupMode;
import org.virtualbox_4_1.IMachine;
import org.virtualbox_4_1.NetworkAttachmentType;
import org.virtualbox_4_1.StorageBus;
import org.virtualbox_4_1.VBoxException;
import org.virtualbox_4_1.VirtualBoxManager;

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.cache.AbstractLoadingCache;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

/**
 * A {@link LoadingCache} for masters. If the requested master has been previously created this
 * returns it, if not it coordinates its creation including downloading isos and creating
 * cache/config directories. This also implements {@link Supplier} in order to provide jetty with
 * the current image (only one master can be created at a time).
 * 
 * @author dralves
 * 
 */
@Singleton
public class MastersLoadingCache extends AbstractLoadingCache<Image, Master> {

   // TODO parameterize
   public static final int MASTER_PORT = 2222;
   public static final String HOST_ONLY_IFACE_NAME = "vboxnet0";

   @Resource
   @Named(ComputeServiceConstants.COMPUTE_LOGGER)
   protected Logger logger = Logger.NULL;

   private final Map<String, Master> masters = Maps.newHashMap();
   private final Function<MasterSpec, IMachine> masterCreatorAndInstaller;
   private final Map<String, YamlImage> imageMapping;
   private final String workingDir;
   private final String installationKeySequence;
   private final String isosDir;
   private final Supplier<VirtualBoxManager> manager;
   private final Function<URI, File> isoDownloader;
   private final String version;
   private final String preconfigurationUrl;

   @Inject
   public MastersLoadingCache(@Named(Constants.PROPERTY_BUILD_VERSION) String version,
            @Named(VIRTUALBOX_INSTALLATION_KEY_SEQUENCE) String installationKeySequence,
            @Named(VIRTUALBOX_PRECONFIGURATION_URL) String preconfigurationUrl,
            @Named(VIRTUALBOX_WORKINGDIR) String workingDir, Function<MasterSpec, IMachine> masterLoader,
            Supplier<Map<Image, YamlImage>> yamlMapper, Supplier<VirtualBoxManager> manager,
            Function<URI, File> isoDownloader) {
      checkNotNull(version, "version");
      checkNotNull(installationKeySequence, "installationKeySequence");
      checkNotNull(manager, "vboxmanager");
      this.manager = manager;
      this.masterCreatorAndInstaller = masterLoader;
      this.installationKeySequence = installationKeySequence;
      this.workingDir = workingDir == null ? VIRTUALBOX_DEFAULT_DIR : workingDir;
      this.isosDir = workingDir + File.separator + "isos";
      this.imageMapping = Maps.newLinkedHashMap();
      for (Entry<Image, YamlImage> entry : yamlMapper.get().entrySet()) {
         this.imageMapping.put(entry.getKey().getId(), entry.getValue());
      }
      this.version = Iterables.get(Splitter.on('r').split(version), 0);
      this.isoDownloader = isoDownloader;
      this.preconfigurationUrl = preconfigurationUrl;
   }

   @PostConstruct
   public void createCacheDirStructure() {
      if (!new File(workingDir).exists()) {
         new File(workingDir, "isos").mkdirs();
      }
   }

   @Override
   public synchronized Master get(Image key) throws ExecutionException {
      // check if we have loaded this machine before
      if (masters.containsKey(key.getId())) {
         return masters.get(key.getId());
      }

      checkState(!key.getId().contains(VIRTUALBOX_NODE_NAME_SEPARATOR), "master image names cannot contain \""
               + VIRTUALBOX_NODE_NAME_SEPARATOR + "\"");

      String vmName = VIRTUALBOX_IMAGE_PREFIX + key.getId();

      IMachine masterMachine;

      Master master;

      // ready the preseed file server
      PreseedCfgServer server = new PreseedCfgServer();
      try {
         // try and find a master machine in vbox
         masterMachine = manager.get().getVBox().findMachine(vmName);
         master = Master.builder().machine(masterMachine).build();

      } catch (VBoxException e) {
         if (machineNotFoundException(e)) {
            // machine was not found try to build one from a yaml file
            YamlImage currentImage = imageMapping.get(key.getId());

            checkNotNull(currentImage);

            server.start(preconfigurationUrl, currentImage.preseed_cfg);

            MasterSpec masterSpec = buildMasterSpecFromYaml(currentImage, vmName);

            // create the master machine if it can't be found
            masterMachine = masterCreatorAndInstaller.apply(masterSpec);

            // build the master
            master = Master.builder().machine(masterMachine).spec(masterSpec).build();
         } else {
            throw e;
         }
      } finally {
         server.stop();
      }

      masters.put(key.getId(), master);
      return master;
   }

   private MasterSpec buildMasterSpecFromYaml(YamlImage currentImage, String vmName) throws ExecutionException {

      String guestAdditionsFileName = String.format("VBoxGuestAdditions_%s.iso", version);
      String guestAdditionsIso = String.format("%s/%s", isosDir, guestAdditionsFileName);
      String guestAdditionsUri = "http://download.virtualbox.org/virtualbox/" + version + "/" + guestAdditionsFileName;
      if (!new File(guestAdditionsIso).exists()) {
         getFilePathOrDownload(guestAdditionsUri);
      }
      checkState(new File(guestAdditionsIso).exists(), "guest additions iso does not exist at: " + guestAdditionsIso);

      // check if the iso is here, download if not
      String localIsoUrl = getFilePathOrDownload(currentImage.iso);

      String adminDisk = workingDir + File.separator + vmName + ".vdi";

      HardDisk hardDisk = HardDisk.builder().diskpath(adminDisk).autoDelete(true).controllerPort(0).deviceSlot(1)
               .build();

      StorageController ideController = StorageController.builder().name("IDE Controller").bus(StorageBus.IDE)
               .attachISO(0, 0, localIsoUrl).attachHardDisk(hardDisk).attachISO(1, 0, guestAdditionsIso).build();

      VmSpec vmSpecification = VmSpec.builder().id(currentImage.id).name(vmName).memoryMB(512).osTypeId("")
               .controller(ideController).forceOverwrite(true).cleanUpMode(CleanupMode.Full).build();

      NetworkAdapter networkAdapter = NetworkAdapter.builder().networkAttachmentType(NetworkAttachmentType.NAT)
               .tcpRedirectRule("127.0.0.1", MASTER_PORT, "", 22).build();

      NetworkInterfaceCard networkInterfaceCard = NetworkInterfaceCard.builder().addNetworkAdapter(networkAdapter)
               .slot(0L).build();

      NetworkSpec networkSpec = NetworkSpec.builder().addNIC(networkInterfaceCard).build();

      return MasterSpec
               .builder()
               .vm(vmSpecification)
               .iso(IsoSpec.builder().sourcePath(localIsoUrl)
                        .installationScript(installationKeySequence.replace("HOSTNAME", vmSpecification.getVmName()))
                        .build()).network(networkSpec).build();
   }

   @Override
   public synchronized Master getIfPresent(Object key) {
      checkArgument(key instanceof Image, "this cache is for entries who's keys are Images");
      Image image = Image.class.cast(key);
      if (masters.containsKey(image.getId())) {
         return masters.get(image.getId());
      }
      return null;
   }

   private String getFilePathOrDownload(String httpUrl) throws ExecutionException {
      String fileName = httpUrl.substring(httpUrl.lastIndexOf('/') + 1, httpUrl.length());
      File localFile = new File(isosDir, fileName);
      if (!localFile.exists()) {
         logger.debug("iso not found in cache, downloading: %s", httpUrl);
         localFile = isoDownloader.apply(URI.create(httpUrl));
      }
      checkState(localFile.exists(), "iso file has not been downloaded: " + fileName);
      return localFile.getAbsolutePath();
   }

}
