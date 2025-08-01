/*
 * Copyright (c) 2025, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.webhook.metadata.api.core.cache;

import org.wso2.carbon.identity.core.cache.CacheKey;

/**
 * Cache key for webhook cache.
 * This class uniquely identifies a webhook based on its ID and tenant ID.
 */
public class WebhookMetadataCacheKey extends CacheKey {

    private static final long serialVersionUID = 202406120001L;
    private final int tenantId;

    public WebhookMetadataCacheKey(int tenantId) {

        this.tenantId = tenantId;
    }

    public int getTenantId() {

        return tenantId;
    }

    @Override
    public boolean equals(Object o) {

        if (!(o instanceof WebhookMetadataCacheKey)) {
            return false;
        }
        return tenantId == ((WebhookMetadataCacheKey) o).tenantId;
    }

    @Override
    public int hashCode() {

        return Integer.hashCode(tenantId);
    }
}
