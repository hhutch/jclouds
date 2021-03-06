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
package org.jclouds.openstack.nova.v1_1.config;

import java.lang.reflect.Type;
import java.util.Map;

import javax.inject.Singleton;

import org.jclouds.json.config.GsonModule;
import org.jclouds.json.config.GsonModule.DateAdapter;
import org.jclouds.openstack.nova.v1_1.domain.HostResourceUsage;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;

/**
 * @author Adrian Cole
 */
public class NovaParserModule extends AbstractModule {

   @Provides
   @Singleton
   public Map<Type, Object> provideCustomAdapterBindings() {
      return ImmutableMap.<Type, Object> of(HostResourceUsage.class, new HostResourceUsageAdapter());
   }

   @Override
   protected void configure() {
      bind(DateAdapter.class).to(GsonModule.Iso8601DateAdapter.class);
   }

   @Singleton
   public static class HostResourceUsageAdapter implements JsonSerializer<HostResourceUsage>, JsonDeserializer<HostResourceUsage> {
      public HostResourceUsage apply(HostResourceUsageWrapper in) {
         return in.resource.toBuilder().build();
      }
      
      @Override
      public HostResourceUsage deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context) throws JsonParseException {
         return apply((HostResourceUsageWrapper) context.deserialize(jsonElement, HostResourceUsageWrapper.class));
      }

      @Override
      public JsonElement serialize(HostResourceUsage hostResourceUsage, Type type, JsonSerializationContext context) {
         return context.serialize(hostResourceUsage);
      }
      
      private static class HostResourceUsageWrapper {
         protected HostResourceUsageInternal resource;
      }
      private static class HostResourceUsageInternal extends HostResourceUsage {
         protected HostResourceUsageInternal(Builder<?> builder) {
            super(builder);
         }
      }
   }

}
