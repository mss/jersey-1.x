/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.jersey.client.apache4.impl;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.container.filter.LoggingFilter;
import com.sun.jersey.api.core.DefaultResourceConfig;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.client.apache4.ApacheHttpClient4;
import com.sun.jersey.client.apache4.config.ApacheHttpClient4Config;
import com.sun.jersey.client.apache4.config.DefaultApacheHttpClient4Config;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 *
 * @author Paul.Sandoz@Sun.Com
 */
public class HttpHeadersTest extends AbstractGrizzlyServerTester {
    @Path("/test")
    public static class HttpMethodResource {
        @POST
        public String post(
                @HeaderParam("Transfer-Encoding") String transferEncoding,
                @HeaderParam("Content-Length") Integer contentLength,
                @HeaderParam("X-CLIENT") String xClient,
                @HeaderParam("X-WRITER") String xWriter,
                @HeaderParam("X-CHUNKED") String xChunked,
                String entity) {
            assertEquals("client", xClient);
            if (transferEncoding == null)
                transferEncoding = "identity";
            assertEquals("Transfer-Encoding", xChunked, transferEncoding);
            assertEquals("Content-Length", xChunked.equals("chunked") ? null : 4, contentLength);
            if (!transferEncoding.equals("chunked"))
                assertEquals("writer", xWriter);
            return entity;
        }
    }

    @Provider
    @Produces("text/plain")
    public static class HeaderWriter implements MessageBodyWriter<String> {

        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return type == String.class;
        }

        public long getSize(String t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return -1;
        }

        public void writeTo(String t, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
            httpHeaders.add("X-WRITER", "writer");
            entityStream.write(t.getBytes());
        }
    }

    public HttpHeadersTest(String testName) {
        super(testName);
    }
    
    ClientResponse post(Integer chunkedEncodingSize) {
        DefaultApacheHttpClient4Config config = new DefaultApacheHttpClient4Config();
        config.getClasses().add(HeaderWriter.class);
        config.getProperties().put(ApacheHttpClient4Config.PROPERTY_CHUNKED_ENCODING_SIZE, chunkedEncodingSize);
        ApacheHttpClient4 c = ApacheHttpClient4.create(config);
        
        WebResource r = c.resource(getUri().path("test").build());

        boolean chunked = chunkedEncodingSize != null && chunkedEncodingSize.intValue() != 0;
        return r
                .header("X-CLIENT", "client")
                .header("X-CHUNKED", chunked ? "chunked" : "identity")
                .post(ClientResponse.class, "POST");
    }
    
    public void testPost() {
        startServer(HttpMethodResource.class);

        ClientResponse cr = post(null);
        assertEquals(200, cr.getStatus());
        assertTrue(cr.hasEntity());
        cr.close();
    }
    
    public void testPostUnchunked() {
        ResourceConfig rc = new DefaultResourceConfig(HttpMethodResource.class);
        rc.getProperties().put(ResourceConfig.PROPERTY_CONTAINER_REQUEST_FILTERS,
                LoggingFilter.class.getName());
        startServer(rc);

        ClientResponse cr = post(null);
        assertEquals(200, cr.getStatus());
        assertTrue(cr.hasEntity());
        cr.close();
    }

    public void testPostChunked() {
        ResourceConfig rc = new DefaultResourceConfig(HttpMethodResource.class);
        rc.getProperties().put(ResourceConfig.PROPERTY_CONTAINER_REQUEST_FILTERS,
                LoggingFilter.class.getName());
        startServer(rc);

        ClientResponse cr = post(-1);
        assertEquals(200, cr.getStatus());
        assertTrue(cr.hasEntity());
        cr.close();
    }
}
