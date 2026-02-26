package com.ryanbern.protonavigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ProtoNameConverterTest {

    @Nested
    inner class CamelToSnake {
        @Test
        fun `simple camelCase`() {
            assertEquals("idempotency_key", ProtoNameConverter.camelToSnake("IdempotencyKey"))
        }

        @Test
        fun `single word`() {
            assertEquals("cuid", ProtoNameConverter.camelToSnake("Cuid"))
        }

        @Test
        fun `already lowercase`() {
            assertEquals("cuid", ProtoNameConverter.camelToSnake("cuid"))
        }

        @Test
        fun `multiple words`() {
            assertEquals("created_at", ProtoNameConverter.camelToSnake("CreatedAt"))
        }

        @Test
        fun `consecutive uppercase (acronym)`() {
            assertEquals("btc_usd_price", ProtoNameConverter.camelToSnake("BTCUsdPrice"))
        }

        @Test
        fun `empty string`() {
            assertEquals("", ProtoNameConverter.camelToSnake(""))
        }

        @Test
        fun `trailing acronym`() {
            assertEquals("get_btc", ProtoNameConverter.camelToSnake("GetBTC"))
        }

        @Test
        fun `bitcoin price`() {
            assertEquals("bitcoin_price", ProtoNameConverter.camelToSnake("BitcoinPrice"))
        }
    }

    @Nested
    inner class MethodToProtoFieldName {
        @Test
        fun `getter method`() {
            assertEquals("cuid", ProtoNameConverter.methodToProtoFieldName("getCuid"))
        }

        @Test
        fun `has method`() {
            assertEquals("created_at", ProtoNameConverter.methodToProtoFieldName("hasCreatedAt"))
        }

        @Test
        fun `set method`() {
            assertEquals("balance", ProtoNameConverter.methodToProtoFieldName("setBalance"))
        }

        @Test
        fun `list getter strips List suffix`() {
            assertEquals("balances", ProtoNameConverter.methodToProtoFieldName("getBalancesList"))
        }

        @Test
        fun `no recognized prefix`() {
            assertNull(ProtoNameConverter.methodToProtoFieldName("doSomething"))
        }

        @Test
        fun `too short after stripping prefix`() {
            assertNull(ProtoNameConverter.methodToProtoFieldName("get"))
        }

        @Test
        fun `multi-word getter`() {
            assertEquals("idempotency_key", ProtoNameConverter.methodToProtoFieldName("getIdempotencyKey"))
        }

        @Test
        fun `does not strip List if that's the entire name`() {
            assertEquals("list", ProtoNameConverter.methodToProtoFieldName("getList"))
        }
    }

    @Nested
    inner class StripGeneratedSuffix {
        @Test
        fun `strip OrBuilder`() {
            assertEquals("Foo", ProtoNameConverter.stripGeneratedSuffix("FooOrBuilder"))
        }

        @Test
        fun `strip GrpcKt`() {
            assertEquals("CreditIntegrationService", ProtoNameConverter.stripGeneratedSuffix("CreditIntegrationServiceGrpcKt"))
        }

        @Test
        fun `strip Grpc`() {
            assertEquals("CreditIntegrationService", ProtoNameConverter.stripGeneratedSuffix("CreditIntegrationServiceGrpc"))
        }

        @Test
        fun `strip Kt`() {
            assertEquals("MoneyMoverEventMeta", ProtoNameConverter.stripGeneratedSuffix("MoneyMoverEventMetaKt"))
        }

        @Test
        fun `no suffix to strip`() {
            assertEquals("GetVaultsForUserRequest", ProtoNameConverter.stripGeneratedSuffix("GetVaultsForUserRequest"))
        }
    }
}
