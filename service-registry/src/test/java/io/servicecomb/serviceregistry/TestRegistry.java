/*
 * Copyright 2017 Huawei Technologies Co., Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.servicecomb.serviceregistry;

import static io.servicecomb.serviceregistry.RegistryUtils.PUBLISH_ADDRESS;

import com.netflix.config.ConcurrentCompositeConfiguration;
import com.netflix.config.ConcurrentMapConfiguration;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import io.servicecomb.config.ConfigUtil;
import io.servicecomb.foundation.common.net.NetUtils;
import io.servicecomb.serviceregistry.api.registry.HealthCheck;
import io.servicecomb.serviceregistry.api.registry.HealthCheckMode;
import io.servicecomb.serviceregistry.api.registry.Microservice;
import io.servicecomb.serviceregistry.api.registry.MicroserviceInstance;
import io.servicecomb.serviceregistry.api.registry.MicroserviceInstanceStatus;
import io.servicecomb.serviceregistry.api.registry.MicroserviceStatus;
import io.servicecomb.serviceregistry.api.response.HeartbeatResponse;
import io.servicecomb.serviceregistry.api.response.MicroserviceInstanceChangedEvent;
import io.servicecomb.serviceregistry.cache.CacheRegistryListener;
import io.servicecomb.serviceregistry.client.http.ServiceRegistryClientImpl;
import io.servicecomb.serviceregistry.notify.NotifyManager;
import io.servicecomb.serviceregistry.notify.RegistryEvent;
import io.servicecomb.serviceregistry.utils.Timer;
import io.servicecomb.serviceregistry.utils.TimerException;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mockit.Expectations;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import org.apache.commons.configuration.AbstractConfiguration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

public class TestRegistry {

    private static final AbstractConfiguration inMemoryConfig = new ConcurrentMapConfiguration();

    @BeforeClass
    public static void initSetup() throws Exception {
        AbstractConfiguration dynamicConfig = ConfigUtil.createDynamicConfig();
        ConcurrentCompositeConfiguration configuration = new ConcurrentCompositeConfiguration();
        configuration.addConfiguration(dynamicConfig);
        configuration.addConfiguration(inMemoryConfig);

        ConfigurationManager.install(configuration);
        RegistryUtils.setSrClient(null);
    }

    @Before
    public void setUp() throws Exception {
        inMemoryConfig.clear();
    }

    @After
    public void tearDown() throws Exception {
        RegistryUtils.destory();
    }

    @Test
    public void testRegistryUtils() throws Exception {
        Microservice oInstance = RegistryUtils.getMicroservice();
        List<String> schemas = new ArrayList<>();
        schemas.add("testSchema");
        oInstance.setSchemas(schemas);
        oInstance.setServiceId("testServiceId");
        oInstance.setStatus(MicroserviceStatus.UNKNOWN.name());
        Map<String, String> properties = new HashMap<>();
        properties.put("proxy", "testPorxy");
        oInstance.setProperties(properties);

        Assert.assertEquals("default", oInstance.getServiceName());
        Assert.assertEquals("default", oInstance.getAppId());
        Assert.assertEquals("", oInstance.getDescription());
        Assert.assertEquals("FRONT", oInstance.getLevel());
        Assert.assertEquals("testPorxy", oInstance.getProperties().get("proxy"));
        Assert.assertEquals("testServiceId", oInstance.getServiceId());
        Assert.assertEquals("0.0.1", oInstance.getVersion());
        Assert.assertEquals(1, oInstance.getSchemas().size());
        Assert.assertEquals(MicroserviceStatus.UNKNOWN.name(), oInstance.getStatus());

        RegistryUtils.getMicroserviceInstance().setHostName("test");
        RegistryUtils.getMicroserviceInstance().setServiceId("testServiceID");
        RegistryUtils.getMicroserviceInstance().setInstanceId("testID");
        RegistryUtils.getMicroserviceInstance().setStage("testStage");

        List<String> endpoints = new ArrayList<>();
        endpoints.add("localhost");

        RegistryUtils.getMicroserviceInstance().setEndpoints(endpoints);
        RegistryUtils.getMicroserviceInstance().setStatus(MicroserviceInstanceStatus.STARTING);
        RegistryUtils.getMicroserviceInstance().setProperties(properties);

        HealthCheck oHealthCheck = new HealthCheck();
        oHealthCheck.setInterval(10);
        oHealthCheck.setPort(8080);
        oHealthCheck.setTimes(20);
        HealthCheckMode oHealthCheckMode = HealthCheckMode.PLATFORM;
        oHealthCheck.setMode(oHealthCheckMode);

        RegistryUtils.getMicroserviceInstance().setHealthCheck(oHealthCheck);

        Assert.assertEquals("test", RegistryUtils.getMicroserviceInstance().getHostName());
        Assert.assertEquals("testServiceID", RegistryUtils.getMicroserviceInstance().getServiceId());
        Assert.assertEquals("testID", RegistryUtils.getMicroserviceInstance().getInstanceId());
        Assert.assertEquals(endpoints, RegistryUtils.getMicroserviceInstance().getEndpoints());
        Assert.assertEquals(MicroserviceInstanceStatus.STARTING, RegistryUtils.getMicroserviceInstance().getStatus());
        Assert.assertEquals(10, RegistryUtils.getMicroserviceInstance().getHealthCheck().getInterval());
        Assert.assertEquals(8080, RegistryUtils.getMicroserviceInstance().getHealthCheck().getPort());
        Assert.assertEquals(20, RegistryUtils.getMicroserviceInstance().getHealthCheck().getTimes());
        Assert.assertEquals("pull", RegistryUtils.getMicroserviceInstance().getHealthCheck().getMode().getName());
        Assert.assertEquals(0, RegistryUtils.getMicroserviceInstance().getHealthCheck().getTTL());
        RegistryUtils.getMicroserviceInstance().getHealthCheck().setMode(HealthCheckMode.HEARTBEAT);
        Assert.assertNotEquals(0, RegistryUtils.getMicroserviceInstance().getHealthCheck().getTTL());
        Assert.assertEquals("testPorxy", RegistryUtils.getMicroserviceInstance().getProperties().get("proxy"));
        Assert.assertEquals("testStage", RegistryUtils.getMicroserviceInstance().getStage());

    }

    @Test
    public void testRegistryUtilsWithStub(
            final @Mocked ServiceRegistryClientImpl oMockServiceRegistryClient) throws Exception {
        HeartbeatResponse response = new HeartbeatResponse();
        response.setOk(true);
        response.setMessage("OK");

        new Expectations() {
            {
                oMockServiceRegistryClient.init();
                oMockServiceRegistryClient.registerMicroservice((Microservice) any);
                result = "sampleServiceID";
                oMockServiceRegistryClient.registerMicroserviceInstance((MicroserviceInstance) any);
                result = "sampleInstanceID";
                oMockServiceRegistryClient.unregisterMicroserviceInstance(anyString, anyString);
                result = true;
            }
        };

        RegistryUtils.setSrClient(oMockServiceRegistryClient);
        RegistryUtils.init();
        Assert.assertEquals(true, RegistryUtils.unregsiterInstance());
    }

    @Test
    public void testRegistryUtilsWithStubFailure(
            final @Mocked ServiceRegistryClientImpl oMockServiceRegistryClient) throws Exception {
        new Expectations() {
            {
                oMockServiceRegistryClient.init();
                oMockServiceRegistryClient.registerMicroservice((Microservice) any);
                result = "sampleServiceID";
                oMockServiceRegistryClient.registerMicroserviceInstance((MicroserviceInstance) any);
                result = "sampleInstanceID";
                oMockServiceRegistryClient.unregisterMicroserviceInstance(anyString, anyString);
                result = false;
            }
        };

        RegistryUtils.setSrClient(oMockServiceRegistryClient);
        RegistryUtils.init();
        Assert.assertEquals(false, RegistryUtils.unregsiterInstance());
    }

    @Test
    public void testRegistryUtilsWithStubHeartbeat(
            final @Mocked ServiceRegistryClientImpl oMockServiceRegistryClient) throws Exception {
        HeartbeatResponse response = new HeartbeatResponse();
        response.setOk(true);
        response.setMessage("OK");

        new Expectations() {
            {
                oMockServiceRegistryClient.init();
                oMockServiceRegistryClient.registerMicroservice((Microservice) any);
                result = "sampleServiceID";
                oMockServiceRegistryClient.registerMicroserviceInstance((MicroserviceInstance) any);
                result = "sampleInstanceID";
                oMockServiceRegistryClient.heartbeat(anyString, anyString);
                result = response;
                oMockServiceRegistryClient.unregisterMicroserviceInstance(anyString, anyString);
                result = false;
            }
        };

        RegistryUtils.setSrClient(oMockServiceRegistryClient);
        RegistryUtils.init();
        Assert.assertEquals(true, RegistryUtils.heartbeat().isOk());
        Assert.assertEquals(false, RegistryUtils.unregsiterInstance());
    }

    @Test
    public void testRegistryUtilsWithStubHeartbeatFailure(
            final @Mocked ServiceRegistryClientImpl oMockServiceRegistryClient) throws Exception {
        final HeartbeatResponse response = new HeartbeatResponse();
        response.setOk(false);
        response.setMessage("FAIL");

        new Expectations() {
            {
                oMockServiceRegistryClient.init();
                oMockServiceRegistryClient.registerMicroservice((Microservice) any);
                result = "sampleServiceID";
                oMockServiceRegistryClient.registerMicroserviceInstance((MicroserviceInstance) any);
                result = "sampleInstanceID";
                oMockServiceRegistryClient.heartbeat(anyString, anyString);
                result = response;
                oMockServiceRegistryClient.unregisterMicroserviceInstance(anyString, anyString);
                result = false;
            }
        };

        RegistryUtils.setSrClient(oMockServiceRegistryClient);
        RegistryUtils.init();
        Assert.assertEquals(false, RegistryUtils.heartbeat().isOk());
        Assert.assertEquals(false, RegistryUtils.unregsiterInstance());
    }

    @Test
    public void testAllowCrossApp() {
        Map<String, String> propertiesMap = new HashMap<>();
        Assert.assertFalse(RegistryUtils.allowCrossApp(propertiesMap));

        propertiesMap.put("allowCrossApp", "true");
        Assert.assertTrue(RegistryUtils.allowCrossApp(propertiesMap));

        propertiesMap.put("allowCrossApp", "false");
        Assert.assertFalse(RegistryUtils.allowCrossApp(propertiesMap));

        propertiesMap.put("allowCrossApp", "asfas");
        Assert.assertFalse(RegistryUtils.allowCrossApp(propertiesMap));
    }

    @Test
    public void testInit() {
        boolean validAssert = true;
        try {
            RegistryUtils.init();
        } catch (Exception e) {
            validAssert = false;
        }
        Assert.assertTrue(validAssert);

    }

    @Test
    public void testRegsiterInstanceEmpty(@Mocked ServiceRegistryClientImpl oMockServiceRegistryClient) {
        RegistryUtils.getMicroserviceInstance().setHostName("test");
        RegistryUtils.getMicroserviceInstance().setServiceId("testServiceID");
        RegistryUtils.getMicroserviceInstance().setInstanceId("testID");
        RegistryUtils.getMicroserviceInstance().setStage("testStage");

        new Expectations() {
            {

                oMockServiceRegistryClient.registerMicroserviceInstance((MicroserviceInstance) any);
                result = "";

            }
        };
        try {
            boolean assertValid = RegistryUtils.regsiterInstance();
            Assert.assertFalse(assertValid);

        } catch (Exception e) {
            Assert.assertNotNull(e);
        }
    }

    @Test
    public void testregsiterMicroserviceServiceIdEmpty(@Mocked ServiceRegistryClientImpl oMockServiceRegistryClient) {
        RegistryUtils.getMicroserviceInstance().setHostName("test");
        RegistryUtils.getMicroserviceInstance().setServiceId("testServiceID");
        RegistryUtils.getMicroserviceInstance().setInstanceId("testID");
        RegistryUtils.getMicroserviceInstance().setStage("testStage");
        new Expectations() {
            {
                oMockServiceRegistryClient.getMicroserviceId(RegistryUtils.getMicroservice().getAppId(),
                        RegistryUtils.getMicroservice().getServiceName(),
                        RegistryUtils.getMicroservice().getVersion());
                result = "test";

            }
        };
        boolean validAssert = true;
        try {
            RegistryUtils.init();
        } catch (Exception e) {
            validAssert = false;
        }
        Assert.assertTrue(validAssert);
    }

    @Test
    public void testRegistryUtilsWithStubHeartbeatFailureException(
            final @Mocked ServiceRegistryClientImpl oMockServiceRegistryClient) throws Exception {
        HeartbeatResponse response = new HeartbeatResponse();
        response.setOk(true);
        response.setMessage("OK");
        try {
            new Expectations() {
                {
                    oMockServiceRegistryClient.init();
                    oMockServiceRegistryClient.registerMicroservice((Microservice) any);
                    result = "sampleServiceID";
                    oMockServiceRegistryClient.registerMicroserviceInstance((MicroserviceInstance) any);
                    result = "sampleInstanceID";
                    oMockServiceRegistryClient.heartbeat(anyString, anyString);
                    result = null;

                }
            };

            RegistryUtils.setSrClient(oMockServiceRegistryClient);
            RegistryUtils.init();

            new MockUp<Timer>() {
                @Mock
                public void sleep() throws TimerException {
                    throw new TimerException();
                }
            };

            boolean validAssert = RegistryUtils.heartbeat().isOk();
            Assert.assertTrue(validAssert);
        } catch (Exception e) {
            Assert.assertEquals("java.lang.NullPointerException", e.getClass().getName());
        }

    }

    @Test
    public void testFindServiceInstance() {
        List<MicroserviceInstance> microserviceInstanceList =
            RegistryUtils.findServiceInstance("appId", "serviceName", "versionRule");
        Assert.assertNull(microserviceInstanceList);
    }

    @Test
    public void testFindServiceInstanceWithMicroServiceInstance() {
        List<MicroserviceInstance> microserviceInstanceList = new ArrayList<MicroserviceInstance>();
        microserviceInstanceList.add(new MicroserviceInstance());

        new MockUp<ServiceRegistryClientImpl>() {
            @Mock
            List<MicroserviceInstance> findServiceInstance(String selfMicroserviceId, String appId,
                    String serviceName,
                    String versionRule) {
                return microserviceInstanceList;

            }
        };

        List<MicroserviceInstance> microserviceInstanceListt =
            RegistryUtils.findServiceInstance("appId", "serviceName", "versionRule");
        Assert.assertNotNull(microserviceInstanceListt);
    }

    @Test
    public void testNotifyRegistryEventINSTANCE_CHANGED() {
        boolean status = true;
        new MockUp<CacheRegistryListener>() {
            @Mock
            public void onMicroserviceInstanceChanged(MicroserviceInstanceChangedEvent changedEvent) {
            }
        };
        try {
            NotifyManager.INSTANCE.notifyListeners(RegistryEvent.INSTANCE_CHANGED, null);
        } catch (Exception e) {
            status = false;
        }
        Assert.assertTrue(status);
    }

    @Test
    public void testNotifyRegistryEventINSTANCE_CHANGEDWITHEXCEPTION() {
        boolean status = true;
        try {
            NotifyManager.INSTANCE.notifyListeners(RegistryEvent.INSTANCE_CHANGED, null);
        } catch (Exception e) {
            status = false;
        }
        Assert.assertTrue(status);

    }

    @Test
    public void testNotifyRegistryEventEXCEPTION() {
        boolean status = true;
        new MockUp<CacheRegistryListener>() {
            @Mock
            public void onMicroserviceInstanceChanged(MicroserviceInstanceChangedEvent changedEvent) {
            }
        };
        try {
            NotifyManager.INSTANCE.notifyListeners(RegistryEvent.EXCEPTION, null);
        } catch (Exception e) {
            status = false;
        }
        Assert.assertTrue(status);
    }

    public void testRegistryUtilGetPublishAddress(@Mocked InetAddress ethAddress) {
        new Expectations(NetUtils.class) {
            {
                NetUtils.getHostAddress();
                result = "1.1.1.1";
            }
        };
        String address = RegistryUtils.getPublishAddress();
        Assert.assertEquals("1.1.1.1", address);

        new Expectations(DynamicPropertyFactory.getInstance()) {
            {
                DynamicPropertyFactory.getInstance().getStringProperty(PUBLISH_ADDRESS, "");
                result = new DynamicStringProperty(PUBLISH_ADDRESS, "") {
                    public String get() {
                        return "127.0.0.1";
                    }
                };
            }
        };
        Assert.assertEquals("127.0.0.1", RegistryUtils.getPublishAddress());

        new Expectations(DynamicPropertyFactory.getInstance()) {
            {
                ethAddress.getHostAddress();
                result = "1.1.1.1";
                NetUtils.ensureGetInterfaceAddress("eth100");
                result = ethAddress;
                DynamicPropertyFactory.getInstance().getStringProperty(PUBLISH_ADDRESS, "");
                result = new DynamicStringProperty(PUBLISH_ADDRESS, "") {
                    public String get() {
                        return "{eth100}";
                    }
                };
            }
        };
        Assert.assertEquals("1.1.1.1", RegistryUtils.getPublishAddress());

    }

    @Test
    public void testRegistryUtilGetHostName(@Mocked InetAddress ethAddress) {
        inMemoryConfig.addProperty("cse.service.registry.client.timeout.request", 2000);

        new Expectations(NetUtils.class) {
            {
                NetUtils.getHostName();
                result = "testHostName";
            }
        };
        String host = RegistryUtils.getPublishHostName();
        Assert.assertEquals("testHostName", host);

        inMemoryConfig.addProperty(PUBLISH_ADDRESS, "127.0.0.1");
        Assert.assertEquals("127.0.0.1", RegistryUtils.getPublishHostName());

        new Expectations(DynamicPropertyFactory.getInstance()) {
            {
                ethAddress.getHostName();
                result = "testHostName";
                NetUtils.ensureGetInterfaceAddress("eth100");
                result = ethAddress;
            }
        };
        inMemoryConfig.addProperty(PUBLISH_ADDRESS, "{eth100}");
        Assert.assertEquals("testHostName", RegistryUtils.getPublishHostName());
    }

    @Test
    public void testGetRealListenAddress() throws Exception {
        new Expectations(NetUtils.class) {
            {
                NetUtils.getHostAddress();
                result = "1.1.1.1";
            }
        };

        Assert.assertEquals("rest://172.0.0.0:8080", RegistryUtils.getPublishAddress("rest", "172.0.0.0:8080"));
        Assert.assertEquals(null, RegistryUtils.getPublishAddress("rest", null));

        URI uri = new URI(RegistryUtils.getPublishAddress("rest", "0.0.0.0:8080"));
        Assert.assertEquals("1.1.1.1:8080", uri.getAuthority());

        new Expectations(DynamicPropertyFactory.getInstance()) {
            {
                DynamicPropertyFactory.getInstance().getStringProperty(PUBLISH_ADDRESS, "");
                result = new DynamicStringProperty(PUBLISH_ADDRESS, "") {
                    public String get() {
                        return "1.1.1.1";
                    }
                };
            }
        };
        Assert.assertEquals("rest://1.1.1.1:8080", RegistryUtils.getPublishAddress("rest", "172.0.0.0:8080"));

        InetAddress ethAddress = Mockito.mock(InetAddress.class);
        Mockito.when(ethAddress.getHostAddress()).thenReturn("1.1.1.1");
        new Expectations(DynamicPropertyFactory.getInstance()) {
            {
                NetUtils.ensureGetInterfaceAddress("eth20");
                result = ethAddress;
                DynamicPropertyFactory.getInstance().getStringProperty(PUBLISH_ADDRESS, "");
                result = new DynamicStringProperty(PUBLISH_ADDRESS, "") {
                    public String get() {
                        return "{eth20}";
                    }
                };
            }
        };
        Assert.assertEquals("rest://1.1.1.1:8080", RegistryUtils.getPublishAddress("rest", "172.0.0.0:8080"));
    }

    @Test
    public void testUpdateInstanceProperties() {
        MicroserviceInstance instance = RegistryUtils.getMicroserviceInstance();
        instance.setServiceId("1");
        instance.setInstanceId("1");
        new MockUp<ServiceRegistryClientImpl>() {
            @Mock
            public boolean updateInstanceProperties(String microserviceId, String microserviceInstanceId,
                    Map<String, String> instanceProperties) {
                return true;
            }
        };
        Assert.assertEquals(1, instance.getProperties().size());
        Map<String, String> properties = new HashMap<>();
        properties.put("tag1", "value1");
        RegistryUtils.updateInstanceProperties(properties);
        Assert.assertEquals(properties, instance.getProperties());

        new MockUp<ServiceRegistryClientImpl>() {
            @Mock
            public boolean updateInstanceProperties(String microserviceId, String microserviceInstanceId,
                    Map<String, String> instanceProperties) {
                return false;
            }
        };
        RegistryUtils.updateInstanceProperties(new HashMap<>());
        Assert.assertEquals(properties, instance.getProperties());
    }

}
