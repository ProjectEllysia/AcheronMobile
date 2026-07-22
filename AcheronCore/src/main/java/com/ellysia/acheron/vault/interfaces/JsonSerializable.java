package com.ellysia.acheron.vault.interfaces;

import com.ellysia.acheron.vault.storables.VaultObject;

import java.security.GeneralSecurityException;

public interface JsonSerializable {
    String toJson() throws GeneralSecurityException;
}
