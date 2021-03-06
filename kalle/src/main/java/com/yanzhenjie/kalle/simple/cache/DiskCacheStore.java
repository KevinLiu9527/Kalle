/*
 * Copyright © 2018 Yan Zhenjie.
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
package com.yanzhenjie.kalle.simple.cache;

import android.text.TextUtils;

import com.yanzhenjie.kalle.Headers;
import com.yanzhenjie.kalle.secure.Encryption;
import com.yanzhenjie.kalle.secure.Secret;
import com.yanzhenjie.kalle.util.IOUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.GeneralSecurityException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>
 * Cache on disk.
 * </p>
 * Created by Yan Zhenjie on 2016/10/15.
 */
public class DiskCacheStore implements CacheStore {

    public static Builder newBuilder(String directory) {
        return new Builder(directory);
    }

    private Lock mLock;

    private Secret mSecret;
    private String mDirectory;

    private DiskCacheStore(Builder builder) {
        mLock = new ReentrantLock();

        mDirectory = builder.mDirectory;
        String password = TextUtils.isEmpty(builder.mPassword) ? mDirectory : builder.mPassword;
        mSecret = Encryption.createSecret(password);
    }

    @Override
    public Cache get(String key) {
        mLock.lock();
        key = uniqueKey(key);

        BufferedReader bufferedReader = null;
        try {
            File file = new File(mDirectory, key);
            if (!file.exists() || file.isDirectory()) return null;

            Cache cache = new Cache();
            bufferedReader = new BufferedReader(new FileReader(file));
            cache.setCode(Integer.parseInt(decrypt(bufferedReader.readLine())));
            cache.setHeaders(Headers.fromJSONString(decrypt(bufferedReader.readLine())));
            cache.setBody(Encryption.hexToByteArray(decrypt(bufferedReader.readLine())));
            cache.setExpires(Long.parseLong(decrypt(bufferedReader.readLine())));
            return cache;
        } catch (Exception e) {
            IOUtils.delFileOrFolder(new File(mDirectory, key));
        } finally {
            IOUtils.closeQuietly(bufferedReader);
            mLock.unlock();
        }
        return null;
    }

    @Override
    public boolean replace(String key, Cache cache) {
        mLock.lock();
        key = uniqueKey(key);

        BufferedWriter writer = null;
        try {
            if (TextUtils.isEmpty(key) || cache == null) return false;
            if (!IOUtils.createFolder(mDirectory)) return false;

            File file = new File(mDirectory, key);
            if (!IOUtils.createNewFile(file)) return false;

            writer = IOUtils.toBufferedWriter(new FileWriter(file));
            writer.write(encrypt(Integer.toString(cache.getCode())));
            writer.newLine();
            writer.write(encrypt(Headers.toJSONString(cache.getHeaders())));
            writer.newLine();
            writer.write(encrypt(Encryption.byteArrayToHex(cache.getBody())));
            writer.newLine();
            writer.write(encrypt(Long.toString(cache.getExpires())));
            writer.flush();
            return true;
        } catch (Exception e) {
            IOUtils.delFileOrFolder(new File(mDirectory, key));
        } finally {
            IOUtils.closeQuietly(writer);
            mLock.unlock();
        }
        return false;
    }

    @Override
    public boolean remove(String key) {
        mLock.lock();
        key = uniqueKey(key);

        try {
            return IOUtils.delFileOrFolder(new File(mDirectory, key));
        } finally {
            mLock.unlock();
        }
    }

    @Override
    public boolean clear() {
        mLock.lock();
        try {
            return IOUtils.delFileOrFolder(mDirectory);
        } finally {
            mLock.unlock();
        }
    }

    private String encrypt(String encryptionText) throws GeneralSecurityException {
        return mSecret.encrypt(encryptionText);
    }

    private String decrypt(String cipherText) throws GeneralSecurityException {
        return mSecret.decrypt(cipherText);
    }

    private String uniqueKey(String key) {
        return Encryption.getMD5ForString(mDirectory + key);
    }

    public static class Builder {

        private String mDirectory;
        private String mPassword;

        private Builder(String directory) {
            this.mDirectory = directory;
        }

        public Builder password(String password) {
            this.mPassword = password;
            return this;
        }

        public DiskCacheStore build() {
            return new DiskCacheStore(this);
        }
    }

}
