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
package org.jclouds.openstack.swift.v1.internal;

import org.jclouds.http.HttpRequest;
import org.jclouds.http.HttpResponse;
import org.jclouds.openstack.keystone.v2_0.internal.KeystoneFixture;
import org.jclouds.rest.internal.BaseRestClientExpectTest;

/**
 * Base class for writing Swift Expect tests
 * 
 * @author Adrian Cole
 */
public class BaseSwiftExpectTest<T> extends BaseRestClientExpectTest<T> {
   protected HttpRequest keystoneAuthWithUsernameAndPassword;
   protected HttpRequest keystoneAuthWithAccessKeyAndSecretKey;
   protected String authToken;
   protected HttpResponse responseWithKeystoneAccess;
   protected HttpRequest extensionsOfSwiftRequest;
   protected HttpResponse extensionsOfSwiftResponse;
   protected HttpResponse unmatchedExtensionsOfSwiftResponse;

   public BaseSwiftExpectTest() {
      provider = "openstack-swift";
      keystoneAuthWithUsernameAndPassword = KeystoneFixture.INSTANCE.initialAuthWithUsernameAndPassword(identity,
            credential);
      keystoneAuthWithAccessKeyAndSecretKey = KeystoneFixture.INSTANCE.initialAuthWithAccessKeyAndSecretKey(identity,
            credential);
      
      authToken = KeystoneFixture.INSTANCE.getAuthToken();
      responseWithKeystoneAccess = KeystoneFixture.INSTANCE.responseWithAccess();
      // now, createContext arg will need tenant prefix
      identity = KeystoneFixture.INSTANCE.getTenantName() + ":" + identity;
   }
}
