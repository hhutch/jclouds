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
package org.jclouds.openstack.nova.ec2;

import static org.jclouds.Constants.PROPERTY_RELAX_HOSTNAME;
import static org.jclouds.Constants.PROPERTY_TRUST_ALL_CERTS;
import static org.jclouds.compute.config.ComputeServiceProperties.RESOURCENAME_DELIMITER;
import static org.jclouds.ec2.reference.EC2Constants.PROPERTY_EC2_AMI_OWNERS;
import static org.jclouds.ec2.reference.EC2Constants.PROPERTY_EC2_AUTO_ALLOCATE_ELASTIC_IPS;
import static org.jclouds.location.reference.LocationConstants.PROPERTY_REGIONS;

import java.util.Properties;

import org.jclouds.apis.ApiMetadata;
import org.jclouds.ec2.EC2ApiMetadata;
import org.jclouds.ec2.EC2AsyncClient;
import org.jclouds.ec2.EC2Client;
import org.jclouds.ec2.compute.config.EC2ResolveImagesModule;
import org.jclouds.openstack.nova.ec2.config.NovaEC2ComputeServiceContextModule;
import org.jclouds.openstack.nova.ec2.config.NovaEC2RestClientModule;
import org.jclouds.rest.RestContext;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;
import com.google.inject.Module;

/**
 * Implementation of {@link ApiMetadata} for the OpenStack Nova's EC2-clone API
 * 
 * @author Adrian Cole
 */
public class NovaEC2ApiMetadata extends EC2ApiMetadata {

   /** The serialVersionUID */
   private static final long serialVersionUID = -1492951757032303845L;
   
   public static final TypeToken<RestContext<EC2Client, EC2AsyncClient>> CONTEXT_TOKEN = new TypeToken<RestContext<EC2Client, EC2AsyncClient>>() {
      private static final long serialVersionUID = -5070937833892503232L;
   };
   
   private static Builder builder() {
      return new Builder();
   }

   @Override
   public Builder toBuilder() {
      return builder().fromApiMetadata(this);
   }

   public NovaEC2ApiMetadata() {
      this(builder());
   }

   protected NovaEC2ApiMetadata(Builder builder) {
      super(builder);
   }
   
   public static Properties defaultProperties() {
      Properties properties = EC2ApiMetadata.defaultProperties();
      properties.setProperty(PROPERTY_REGIONS, "nova");
      properties.setProperty(PROPERTY_EC2_AMI_OWNERS, "admin");
      // hash characters are banned
      properties.setProperty(RESOURCENAME_DELIMITER, "-");
      // often, we are dealing with IP addresses, not hostnames
      properties.setProperty(PROPERTY_RELAX_HOSTNAME, "true");
      properties.setProperty(PROPERTY_TRUST_ALL_CERTS, "true");
      properties.setProperty(PROPERTY_EC2_AUTO_ALLOCATE_ELASTIC_IPS, "true");
      // auth fail sometimes happens in EC2, as the rc.local script that injects the
      // authorized key executes after ssh has started.  
      properties.setProperty("jclouds.ssh.max-retries", "7");
      properties.setProperty("jclouds.ssh.retry-auth", "true");
      return properties;
   }

   public static class Builder extends EC2ApiMetadata.Builder {
      protected Builder(){
         super(EC2Client.class, EC2AsyncClient.class);
         id("openstack-nova-ec2")
         .name("OpenStack Nova's EC2-clone API")
         .version("2009-04-04")
         .defaultEndpoint("http://localhost:8773/services/Cloud")
         .defaultProperties(NovaEC2ApiMetadata.defaultProperties())
         .defaultModules(ImmutableSet.<Class<? extends Module>>of(NovaEC2RestClientModule.class, EC2ResolveImagesModule.class, NovaEC2ComputeServiceContextModule.class));
      }
      
      @Override
      public NovaEC2ApiMetadata build() {
         return new NovaEC2ApiMetadata(this);
      }

      @Override
      public Builder fromApiMetadata(ApiMetadata in) {
         super.fromApiMetadata(in);
         return this;
      }
   }

}