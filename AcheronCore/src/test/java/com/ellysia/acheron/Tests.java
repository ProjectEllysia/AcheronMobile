package com.ellysia.acheron;

import com.ellysia.acheron.secrets.symmetric.Argon2VaultEncryptingStrategyTest;
import com.ellysia.acheron.secrets.symmetric.PBKDF2VaultEncryptingStrategyTest;
import com.ellysia.acheron.vault.VaultTest;
import com.ellysia.acheron.vault.storables.AccountTest;
import com.ellysia.acheron.vault.storables.CreditCardTest;
import com.ellysia.acheron.vault.storables.VaultObjectCompareToTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
        Argon2VaultEncryptingStrategyTest.class,
        PBKDF2VaultEncryptingStrategyTest.class,
        VaultObjectCompareToTest.class,
        AccountTest.class,
        CreditCardTest.class,
        VaultTest.class,
})
public class Tests {
}
