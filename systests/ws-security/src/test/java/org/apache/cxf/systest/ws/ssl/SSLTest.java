/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.systest.ws.ssl;

import java.io.IOException;
import java.net.URL;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;

import org.apache.cxf.Bus;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.configuration.jsse.SSLUtils;
import org.apache.cxf.systest.ws.common.SecurityTestUtil;
import org.apache.cxf.systest.ws.wssec10.client.UTPasswordCallback;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.cxf.ws.security.SecurityConstants;
import org.example.contract.doubleit.DoubleItPortType;
import org.junit.BeforeClass;

/**
 * A set of tests SSL protocol support.
 */
public class SSLTest extends AbstractBusClientServerTestBase {
    static final String PORT = allocatePort(Server.class);
    static final String PORT2 = allocatePort(Server.class, 2);
    static final String PORT3 = allocatePort(Server.class, 3);
    
    private static final String NAMESPACE = "http://www.example.org/contract/DoubleIt";
    private static final QName SERVICE_QNAME = new QName(NAMESPACE, "DoubleItService");
    
    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue(
            "Server failed to launch",
            // run the server in the same process
            // set this to false to fork
            launchServer(Server.class, true)
        );
    }
    
    public static void cleanup() throws Exception {
        SecurityTestUtil.cleanup();
        stopAllServers();
    }

    @org.junit.Test
    public void testSSLv3NotAllowed() throws Exception {

        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = SSLTest.class.getResource("client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        System.setProperty("https.protocols", "SSLv3");

        URL service = new URL("https://localhost:" + PORT);
        HttpsURLConnection connection = (HttpsURLConnection) service.openConnection();
        
        connection.setHostnameVerifier(new DisableCNCheckVerifier());
        
        SSLContext sslContext = SSLContext.getInstance("SSL");
        URL keystore = SSLTest.class.getResource("../security/Truststore.jks");
        TrustManager[] trustManagers = 
            SSLUtils.getTrustStoreManagers(false, "jks", keystore.getPath(), 
                                           "PKIX", LogUtils.getL7dLogger(SSLTest.class));
        sslContext.init(null, trustManagers, new java.security.SecureRandom());
        
        connection.setSSLSocketFactory(sslContext.getSocketFactory());
        
        try {
            connection.connect();
            fail("Failure expected on an SSLv3 connection attempt");
        } catch (IOException ex) {
            // expected
        }
        
        System.clearProperty("https.protocols");
        
        bus.shutdown(true);
    }
    
    @org.junit.Test
    public void testSSLv3Allowed() throws Exception {

        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = SSLTest.class.getResource("client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        System.setProperty("https.protocols", "SSLv3");

        URL service = new URL("https://localhost:" + PORT2);
        HttpsURLConnection connection = (HttpsURLConnection) service.openConnection();
        
        connection.setHostnameVerifier(new DisableCNCheckVerifier());
        
        SSLContext sslContext = SSLContext.getInstance("SSL");
        URL keystore = SSLTest.class.getResource("../security/Truststore.jks");
        TrustManager[] trustManagers = 
            SSLUtils.getTrustStoreManagers(false, "jks", keystore.getPath(), 
                                           "PKIX", LogUtils.getL7dLogger(SSLTest.class));
        sslContext.init(null, trustManagers, new java.security.SecureRandom());
        
        connection.setSSLSocketFactory(sslContext.getSocketFactory());
        
        connection.connect();
        
        connection.disconnect();
        
        System.clearProperty("https.protocols");
        
        bus.shutdown(true);
    }
    
    @org.junit.Test
    public void testClientSSL3NotAllowed() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = SSLTest.class.getResource("client.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = SSLTest.class.getResource("DoubleItSSL.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItPlaintextPort3");
        DoubleItPortType utPort = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(utPort, PORT3);
        
        ((BindingProvider)utPort).getRequestContext().put(SecurityConstants.USERNAME, "Alice");
        ((BindingProvider)utPort).getRequestContext().put(SecurityConstants.CALLBACK_HANDLER,
                                                          new UTPasswordCallback());
        
        try {
            utPort.doubleIt(25);
            fail("Failure expected on the client not supporting SSLv3 by default");
        } catch (Exception ex) {
            // expected
        }
        
        ((java.io.Closeable)utPort).close();
        bus.shutdown(true);
    }
    
    @org.junit.Test
    public void testClientSSL3Allowed() throws Exception {
        SpringBusFactory bf = new SpringBusFactory();
        URL busFile = SSLTest.class.getResource("client-ssl3.xml");

        Bus bus = bf.createBus(busFile.toString());
        SpringBusFactory.setDefaultBus(bus);
        SpringBusFactory.setThreadDefaultBus(bus);
        
        URL wsdl = SSLTest.class.getResource("DoubleItSSL.wsdl");
        Service service = Service.create(wsdl, SERVICE_QNAME);
        QName portQName = new QName(NAMESPACE, "DoubleItPlaintextPort3");
        DoubleItPortType utPort = 
                service.getPort(portQName, DoubleItPortType.class);
        updateAddressPort(utPort, PORT3);
        
        ((BindingProvider)utPort).getRequestContext().put(SecurityConstants.USERNAME, "Alice");
        ((BindingProvider)utPort).getRequestContext().put(SecurityConstants.CALLBACK_HANDLER,
                                                          new UTPasswordCallback());
        
        utPort.doubleIt(25);
        
        ((java.io.Closeable)utPort).close();
        bus.shutdown(true);
    }
    
    private static final class DisableCNCheckVerifier implements HostnameVerifier {

        @Override
        public boolean verify(String arg0, SSLSession arg1) {
            return true;
        }
        
    };
}
