CREATE TABLE IF NOT EXISTS IDN_WEBHOOK_METADATA (
    ID INTEGER NOT NULL AUTO_INCREMENT,
    PROPERTY_NAME VARCHAR(100) NOT NULL,
    PROPERTY_TYPE VARCHAR(10) NOT NULL,
    PRIMITIVE_VALUE VARCHAR(255),
    OBJECT_VALUE BLOB,
    TENANT_ID INTEGER NOT NULL,
    PRIMARY KEY (ID),
    UNIQUE (PROPERTY_NAME, TENANT_ID)
);

-- WEBHOOK METADATA --
CREATE INDEX IDX_IDN_WEBHOOK_METADATA_TID ON IDN_WEBHOOK_METADATA (TENANT_ID);
