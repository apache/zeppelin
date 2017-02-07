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

import { HeliumType, } from './helium-type';

export function createDefaultPackage(pkgSearchResult, sce) {
  for (let pkgIdx in pkgSearchResult) {
    const pkg = pkgSearchResult[pkgIdx];
    pkg.pkg.icon = sce.trustAsHtml(pkg.pkg.icon);
    if (pkg.enabled) {
      pkgSearchResult.splice(pkgIdx, 1);
      return pkg;
    }
  }

  // show first available version if package is not enabled
  const result = pkgSearchResult[0];
  pkgSearchResult.splice(0, 1);
  return result;
}

/**
 * create default packages based on `enabled` field and `latest` version.
 *
 * @param pkgSearchResults
 * @param sce angular `$sce` object
 * @returns {Object} including {name, pkgInfo}
 */
export function createDefaultPackages(pkgSearchResults, sce) {
  const defaultPackages = {};
  // show enabled version if any version of package is enabled
  for (let name in pkgSearchResults) {
    const pkgSearchResult = pkgSearchResults[name];
    defaultPackages[name] = createDefaultPackage(pkgSearchResult, sce)
  }

  return defaultPackages;
}

/**
 * @param defaultPackages {name, pkgSearchResult}
 * @param magic
 * @returns {pkgSearchResult}
 */
export function findPackageByMagic(defaultPackages, magic) {
  for (let name in defaultPackages) {
    const pkgSearchResult = defaultPackages[name];
    if (pkgSearchResult.pkg.type === HeliumType.SPELL &&
      pkgSearchResult.pkg.spell.magic === magic) {
      return pkgSearchResult;
    }
  }

  return undefined;
}

/**
 * @param singlePkgSearchResults list of PkgSearchResult for a single package
 * @param version
 * @returns {T} found PkgSearchResult otherwise returns `undefined`
 */
export function findPackageByVersion(singlePkgSearchResults, version) {
  return singlePkgSearchResults.find(psr => {
    return psr.pkg.version === version;
  });
}
