/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

export const HeliumConfFieldType = {
  NUMBER: 'number',
  JSON: 'json',
  STRING: 'string',
};

/**
 * @param persisted <Object> including `type`, `description`, `defaultValue` for each conf key
 * @param spec <Object> including `value` for each conf key
 */
export function mergePersistedConfWithSpec(persisted, spec) {
  const confs = [];

  for(let name in spec) {
    const specField = spec[name];
    const persistedValue = persisted[name];

    const value = (persistedValue) ? persistedValue : specField.defaultValue;
    const merged = {
      name: name, type: specField.type, description: specField.description,
      value: value, defaultValue: specField.defaultValue,
    };

    confs.push(merged);
  }

  return confs;
}

export function createPackageConf(defaultPackages, persistedPackacgeConfs) {
  let packageConfs = {};

  for (let name in defaultPackages) {
    const pkgInfo = defaultPackages[name];

    const configSpec = pkgInfo.pkg.config;
    if (!configSpec) { continue; }

    const version = pkgInfo.pkg.version;
    if (!version) { continue; }

    let config = {};
    if (persistedPackacgeConfs[name] && persistedPackacgeConfs[name][version]) {
      config = persistedPackacgeConfs[name][version];
    }

    const confs = mergePersistedConfWithSpec(config, configSpec);
    packageConfs[name] = confs;
  }

  return packageConfs;
}

export function parseConfigValue(type, stringified) {
  let value = stringified;

  try {
    if (HeliumConfFieldType.NUMBER === type) {
      value = parseFloat(stringified);
    } else if (HeliumConfFieldType.JSON === type) {
      value = JSON.parse(stringified);
    }
  } catch(error) {
    // return just the stringified one
    console.error(`Failed to parse conf type ${type}, value ${value}`);
  }

  return value;
}

/**
 * create persistable config object
 */
export function createPersistableConfig(currentConf) {
  // persist key-value only
  // since other info (e.g type, desc) can be provided by default config
  const filtered = currentConf.reduce((acc, c) => {
    let value = parseConfigValue(c.type, c.value);
    acc[c.name] = value;
    return acc;
  }, {});

  return filtered;
}


