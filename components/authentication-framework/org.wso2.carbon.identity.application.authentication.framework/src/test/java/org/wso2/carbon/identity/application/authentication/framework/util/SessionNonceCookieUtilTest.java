/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.identity.application.authentication.framework.util;

import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.identity.application.mgt.ApplicationManagementService;
import org.wso2.carbon.identity.core.util.IdentityTenantUtil;
import org.wso2.carbon.identity.core.util.IdentityUtil;

import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.wso2.carbon.identity.application.authentication.framework.util.FrameworkConstants.NONCE_COOKIE_WHITELISTED_AUTHENTICATORS_CONFIG;
import static org.wso2.carbon.identity.application.authentication.framework.util.FrameworkUtils.REQUEST_PARAM_APPLICATION;
import static org.wso2.carbon.identity.application.authentication.framework.util.SessionNonceCookieUtil.NONCE_COOKIE;
import static org.wso2.carbon.identity.application.authentication.framework.util.SessionNonceCookieUtil.NONCE_COOKIE_CONFIG;

@Listeners(MockitoTestNGListener.class)
public class SessionNonceCookieUtilTest {

    private static final String SESSION_DATA_KEY_VALUE = "SessionDataKeyValue";
    private static final String SESSION_DATA_KEY_NAME = "sessionDataKey";
    private static final String RELYING_PARTY_NAME = "relyingParty";
    private static final String RELYING_PARTY_VALUE = "relyingPartyValue";
    private static final String TENANT_DOMAIN_NAME = "tenantDomain";
    private static final String TENANT_DOMAIN_VALUE = "carbon.super";
    private static final String APP_ACCESS_URL = "https://test.mock.relyingParty.com";

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private AuthenticationContext context;

    @Captor
    ArgumentCaptor<Cookie> cookieCaptor;

    @Captor
    ArgumentCaptor<String> responseCaptor;

    @BeforeMethod
    public void setUp() throws Exception {

        setPrivateStaticField(SessionNonceCookieUtil.class, "nonceCookieConfig", null);
    }

    @Test
    public void nonceCookieTest() throws ReflectiveOperationException {

        try (MockedStatic<IdentityUtil> identityUtil = mockStatic(IdentityUtil.class)) {

            mockNonceCookieUtils(identityUtil);
            assertTrue(SessionNonceCookieUtil.isNonceCookieEnabled());

            // Creating old session nonce and authentication cookie.
            Cookie[] cookies = getAuthenticationCookies();
            Mockito.when(request.getCookies()).thenReturn(cookies);
            Mockito.when(context.getContextIdentifier()).thenReturn(SESSION_DATA_KEY_VALUE);

            // Validating old session nonce cookie.
            Mockito.when(context.getProperty(cookies[1].getName())).thenReturn(cookies[1].getValue());
            boolean validateNonceCookie = SessionNonceCookieUtil.validateNonceCookie(request, context);
            assertTrue(validateNonceCookie);

            // Stimulate the flow where a new authentication flow is created when there is existing session nonce
            // cookie.
            Mockito.when(context.getContextIdentifier()).thenReturn(SESSION_DATA_KEY_VALUE);
            SessionNonceCookieUtil.addNonceCookie(request, response, context);

            Mockito.verify(response, times(1)).addCookie(cookieCaptor.capture());
            List<Cookie> capturedCookies = cookieCaptor.getAllValues();

            // First, the old cookie has to be cleared with max age 0.
            Cookie removedOldSessionNonce = capturedCookies.get(0);
            assertEquals(removedOldSessionNonce.getName(), NONCE_COOKIE + "-" + SESSION_DATA_KEY_VALUE);
            assertEquals(removedOldSessionNonce.getMaxAge(), TimeUnit.MINUTES.toSeconds(40) * 2);
        }
    }


    @DataProvider(name = "nonceCookieWhitelistedAuthenticatorsData")
    public Object[][] nonceCookieWhitelistedAuthenticatorsData() {

        return new Object [][] {{"Authenticator_1", true}, {"Authenticator_3", false}};
    }

    @Test(dataProvider = "nonceCookieWhitelistedAuthenticatorsData")
    public void nonceCookieWhitelistedAuthenticatorsTest(String authenticator, boolean expectedOutput)
            throws ReflectiveOperationException {

        try (MockedStatic<IdentityUtil> identityUtil = mockStatic(IdentityUtil.class)) {
            mockNonceCookieUtils(identityUtil);
            assertTrue(SessionNonceCookieUtil.isNonceCookieEnabled());

            // Setting the authenticator name from the data provider.
            Mockito.when(context.getCurrentAuthenticator()).thenReturn(authenticator);

            boolean validateNonceCookie = SessionNonceCookieUtil.validateNonceCookie(request, context);
            assertEquals(validateNonceCookie, expectedOutput);
        }
    }

    @Test(dependsOnMethods = "nonceCookieWhitelistedAuthenticatorsTest")
    public void missingNonceCookieTest() throws Exception {

        try (MockedStatic<IdentityUtil> identityUtil = mockStatic(IdentityUtil.class);
             MockedStatic<IdentityTenantUtil> identityTenantUtil = mockStatic(IdentityTenantUtil.class);
             MockedStatic<FrameworkUtils> frameworkUtils = mockStatic(FrameworkUtils.class);
             MockedStatic<ApplicationManagementService> applicationManagementService =
                     mockStatic(ApplicationManagementService.class)) {
            mockUtils(identityUtil, identityTenantUtil, frameworkUtils, applicationManagementService);
            Mockito.when(request.getParameter(SESSION_DATA_KEY_NAME)).thenReturn(SESSION_DATA_KEY_VALUE);
            Mockito.when(request.getParameter(RELYING_PARTY_NAME)).thenReturn(RELYING_PARTY_VALUE);
            Mockito.when(request.getParameter(REQUEST_PARAM_APPLICATION)).thenReturn(RELYING_PARTY_VALUE);
            Mockito.when(context.getContextIdentifier()).thenReturn(SESSION_DATA_KEY_VALUE);

            PrintWriter mockPrintWriter = Mockito.mock(PrintWriter.class);
            Mockito.when(response.getWriter()).thenReturn(mockPrintWriter);

            // Request contains an authentication context but missing the session nonce cookie.
            LoginContextManagementUtil.handleLoginContext(request, response);

            Mockito.verify(mockPrintWriter).write(responseCaptor.capture());
            List<String> responses = responseCaptor.getAllValues();
            Assert.assertNotNull(responses.get(0));

            JSONObject response = new JSONObject(responses.get(0));
            Assert.assertEquals(response.get("status"), "redirect");
            Assert.assertEquals(response.get("redirectUrl"), APP_ACCESS_URL);
        }
    }

    private Cookie[] getAuthenticationCookies() {

        Cookie[] cookies = new Cookie[2];
        cookies[0] = new Cookie(FrameworkConstants.COMMONAUTH_COOKIE, "commonAuthIdValue");
        cookies[1] = new Cookie(NONCE_COOKIE + "-" + SESSION_DATA_KEY_VALUE, "sessionNonceValue");
        return cookies;
    }

    private void mockUtils(MockedStatic<IdentityUtil> identityUtil, MockedStatic<IdentityTenantUtil> identityTenantUtil,
                           MockedStatic<FrameworkUtils> frameworkUtils,
                           MockedStatic<ApplicationManagementService> applicationManagementService) throws Exception {

        mockNonceCookieUtils(identityUtil);

        identityTenantUtil.when(IdentityTenantUtil::isTenantQualifiedUrlsEnabled).thenReturn(false);
        identityTenantUtil.when(IdentityTenantUtil::shouldUseTenantQualifiedURLs).thenReturn(false);
        frameworkUtils.when(() -> FrameworkUtils.getAuthenticationContextFromCache(any())).thenReturn(context);
        frameworkUtils.when(() -> FrameworkUtils.getCookie(any(), any())).thenReturn(null);

        ApplicationManagementService mockApplicationManagementService = mock(ApplicationManagementService.class);
        ServiceProvider mockServiceProvider = mock(ServiceProvider.class);
        applicationManagementService.when(ApplicationManagementService::getInstance)
                .thenReturn(mockApplicationManagementService);
        when(mockApplicationManagementService.getServiceProvider(anyString(), anyString())).thenReturn(
                mockServiceProvider);
        when(mockServiceProvider.getAccessUrl()).thenReturn(APP_ACCESS_URL);
    }

    private void mockNonceCookieUtils(MockedStatic<IdentityUtil> identityUtil) throws ReflectiveOperationException {

        identityUtil.when(() -> IdentityUtil.getProperty(NONCE_COOKIE_CONFIG)).thenReturn("true");
        identityUtil.when(IdentityUtil::getTempDataCleanUpTimeout).thenReturn(Long.parseLong("40"));
        List<String> expectedList = Arrays.asList("Authenticator_1", "Authenticator_2");
        identityUtil.when(() -> IdentityUtil.getPropertyAsList(NONCE_COOKIE_WHITELISTED_AUTHENTICATORS_CONFIG))
                .thenReturn(expectedList);
        setPrivateStaticFinalField(SessionNonceCookieUtil.class, "NONCE_COOKIE_WHITELISTED_AUTHENTICATORS",
                new HashSet<>(expectedList));
    }

    private void setPrivateStaticField(Class<?> clazz, String fieldName, Object newValue)
            throws NoSuchFieldException, IllegalAccessException {

        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, newValue);
    }

    private static void setPrivateStaticFinalField(Class<?> clazz, String fieldName, Object newValue)
            throws ReflectiveOperationException {

        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);

        Field modifiers = Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);

        field.set(null, newValue);
    }
}
