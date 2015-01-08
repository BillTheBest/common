/*
 * Copyright © 2014 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.common.security.authentication;

/**
 * Represents a message signed by a secret key.
 * @param <T> the type of the message object which has been signed.
 */
public interface Signed<T> {
  /**
   * Returns the message object which was signed.
   */
  T getMessage();

  /**
   * Returns the identifier for the secret key used to compute the message digest.
   */
  int getKeyId();

  /**
   * Returns the digest generated against the message.
   */
  byte[] getDigestBytes();
}