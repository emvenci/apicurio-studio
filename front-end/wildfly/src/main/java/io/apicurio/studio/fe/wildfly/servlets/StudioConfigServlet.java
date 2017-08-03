/*
 * Copyright 2017 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.studio.fe.wildfly.servlets;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.studio.fe.wildfly.config.RequestAttributeKeys;
import io.apicurio.studio.shared.beans.StudioConfig;
import io.apicurio.studio.shared.beans.StudioConfigApis;
import io.apicurio.studio.shared.beans.StudioConfigApisType;
import io.apicurio.studio.shared.beans.StudioConfigAuth;
import io.apicurio.studio.shared.beans.StudioConfigAuthType;
import io.apicurio.studio.shared.beans.StudioConfigMode;
import io.apicurio.studio.shared.beans.User;

/**
 * A servlet that generates a "config.js" file, which is used by the Apicurio Studio
 * Angular application on startup.
 * 
 * @author eric.wittmann@gmail.com
 */
public class StudioConfigServlet extends HttpServlet {
    
    private static final long serialVersionUID = -4719439088893434221L;

    /**
     * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        JsonFactory f = new JsonFactory();
        try (JsonGenerator g = f.createGenerator(response.getOutputStream(), JsonEncoding.UTF8)) {
            response.getOutputStream().write("var ApicurioStudioConfig = ".getBytes("UTF-8")); //$NON-NLS-1$ //$NON-NLS-2$
            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(Include.NON_NULL);
            g.setCodec(mapper);
            g.useDefaultPrettyPrinter();

            HttpSession session = request.getSession();
            String token = (String) session.getAttribute(RequestAttributeKeys.TOKEN_KEY);
            User user = (User) session.getAttribute(RequestAttributeKeys.USER_KEY);
            
            if (token == null) {
                System.out.println("Error: 'token' is null!");
                response.sendError(403);
                return;
            }
            if (user == null) {
                System.out.println("Error: 'user' is null!");
                response.sendError(401);
                return;
            }

            StudioConfig config = new StudioConfig();
            
            config.setMode(StudioConfigMode.prod);
            
            config.setAuth(new StudioConfigAuth());
            config.getAuth().setType(StudioConfigAuthType.token);
            config.getAuth().setToken(token);
            config.getAuth().setLogoutUrl(request.getContextPath() + "/logout");
            
            config.setApis(new StudioConfigApis());
            config.getApis().setType(StudioConfigApisType.hub);
            config.getApis().setHubUrl(generateHubApiUrl(request));
            
            config.setUser(user);
            
            g.writeObject(config);

            g.flush();
            response.getOutputStream().write(";".getBytes("UTF-8")); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    /**
     * Generates a URL that the caller can use to access the Hub API.
     * @param request
     */
    private String generateHubApiUrl(HttpServletRequest request) {
        try {
            String url = lookupHubApiUrl();
            if (url == null) {
                url = request.getRequestURL().toString();
                url = new URI(url).resolve("/api-hub").toString();
            }
            return url;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the GitHub OAuth client-secret.
     */
    private String lookupHubApiUrl() {
        String hubUrl = System.getProperty("apicurio.github.config.hubUrl", null);
        if (hubUrl == null) {
            hubUrl = System.getenv("CONFIG_HUB_URL");
        }
        return hubUrl;
    }

}