/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

export const DefaultDisplayType = {
  ELEMENT: 'ELEMENT',
  TABLE: 'TABLE',
  HTML: 'HTML',
  ANGULAR: 'ANGULAR',
  TEXT: 'TEXT',
};

export const DefaultDisplayMagic = {
  '%element': DefaultDisplayType.ELEMENT,
  '%table': DefaultDisplayType.TABLE,
  '%html': DefaultDisplayType.HTML,
  '%angular': DefaultDisplayType.ANGULAR,
  '%text': DefaultDisplayType.TEXT,
};

export class GeneratorWithType {
  constructor(generator, type) {
    // use variable name `data` to keep consistency with backend
    this.data = generator;
    this.type = type;
  }

  static handleDefaultMagic(m) {
    // let's use default display type instead of magic in case of default
    // to keep consistency with backend interpreter
    if (DefaultDisplayMagic[m]) {
      return DefaultDisplayMagic[m];
    } else {
      return m;
    }
  }

  static parseStringGenerator(generator, customDisplayMagic) {
    function availableMagic(magic) {
      return magic && (DefaultDisplayMagic[magic] || customDisplayMagic[magic]);
    }

    const splited = generator.split("\n");

    const gensWithTypes = [];
    let mergedGens = [];
    let previousMagic = DefaultDisplayType.TEXT;

    // create `GeneratorWithType` whenever see available display type.
    for(let i = 0; i < splited.length; i++) {
      const g = splited[i];
      const magic = FrontendInterpreterResult.extractMagic(g);

      // create `GeneratorWithType` only if see new magic
      if (availableMagic(magic) && mergedGens.length > 0) {
        gensWithTypes.push(new GeneratorWithType(mergedGens.join(''), previousMagic));
        mergedGens = [];
      }

      // accumulate `generator` to mergedGens
      if (availableMagic(magic)) {
        const withoutMagic = g.split(magic)[1];
        mergedGens.push(`${withoutMagic}\n`);
        previousMagic = GeneratorWithType.handleDefaultMagic(magic);
      } else {
        mergedGens.push(`${g}\n`);
      }
    }

    // cleanup the last `GeneratorWithType`
    if (mergedGens.length > 0) {
      previousMagic = GeneratorWithType.handleDefaultMagic(previousMagic);
      gensWithTypes.push(new GeneratorWithType(mergedGens.join(''), previousMagic));
    }

    return gensWithTypes;
  }

  /**
   * get 1 `GeneratorWithType` and produce multiples using available displays
   * return an wrapped with a promise to generalize result output which can be
   * object, function or promise
   * @param generatorWithType {GeneratorWithType}
   * @param availableDisplays {Object} Map for available displays
   * @return {Promise<Array<GeneratorWithType>>}
   */
  static produceMultipleGenerator(generatorWithType, customDisplayType) {
    const generator = generatorWithType.getGenerator();
    const type = generatorWithType.getType();

    // if the type is specified, just return it
    // handle non-specified generatorWithTypes only
    if (type) {
      return new Promise((resolve) => { resolve([generatorWithType]); });
    }

    let wrapped;

    if (FrontendInterpreterResult.isFunctionGenerator(generator)) {
      // if generator is a function, we consider it as ELEMENT type.
      wrapped = new Promise((resolve) => {
        const result = [new GeneratorWithType(generator, DefaultDisplayType.ELEMENT)];
        return resolve(result);
      });
    } else if (FrontendInterpreterResult.isPromiseGenerator(generator)) {
      // if generator is a promise,
      wrapped = generator.then(generated => {
        const result =
          GeneratorWithType.parseStringGenerator(generated, customDisplayType);
        return result;
      })

    } else {
      // if generator is a object, parse it to multiples
      wrapped = new Promise((resolve) => {
        const result =
          GeneratorWithType.parseStringGenerator(generator, customDisplayType);
        return resolve(result);
      });
    }

    return wrapped;
  }

  /**
   * generator (`data`) can be promise, function or just object
   * - if data is an object, it will be used directly.
   * - if data is a function, it will be called with DOM element id
   *   where the final output is rendered.
   * - if data is a promise, the post processing logic
   *   will be called in `then()` of this promise.
   * @returns {*} `data` which can be object, function or promise.
   */
  getGenerator() {
    return this.data;
  }

  /**
   * Value of `type` might be empty which means
   * generator can be splited into multiple generators
   * by `FrontendInterpreterResult.parseMultipleGenerators()`
   * @returns {string}
   */
  getType() {
    return this.type;
  }
}

export class FrontendInterpreterResult {
  constructor(resultGenerator, resultType) {
    this.generatorsWithTypes = [];
    this.add(resultGenerator, resultType);
  }

  static isFunctionGenerator(generator) {
    return (generator && typeof generator === 'function');
  }

  static isPromiseGenerator(generator) {
    return (generator && typeof generator.then === 'function');
  }

  static isObjectGenerator(generator) {
    return (generator &&
      !FrontendInterpreterResult.isFunctionGenerator(generator) &&
      !FrontendInterpreterResult.isPromiseGenerator(generator));
  }

  static extractMagic(allParagraphText) {
    const intpNameRegexp = /^\s*%(\S+)\s*/g;
    try {
      let match = intpNameRegexp.exec(allParagraphText);
      if (match) {
        return `%${match[1].trim()}`;
      }
    } catch (error) {
      // failed to parse, ignore
    }

    return undefined;
  }

  /**
   * consume 1 generator and produce multiple
   * @param generator {string}
   * @param customDisplayType
   * @return {Array<GeneratorWithType>}
   */

  add(resultGenerator, resultType) {
    if (resultGenerator) {
      this.generatorsWithTypes.push(
        new GeneratorWithType(resultGenerator, resultType));
    }

    return this;
  }

  /**
   * @param customDisplayType
   * @return {Promise<Array<GeneratorWithType>>}
   */
  getAllParsedGeneratorsWithTypes(customDisplayType) {
    const promises = this.generatorsWithTypes.map(gt => {
      return GeneratorWithType.produceMultipleGenerator(gt, customDisplayType);
    });

    // some promises can include an array so we need to flatten them
    const flatten = Promise.all(promises).then(values => {
      return values.reduce((acc, cur) => {
        if (Array.isArray(cur)) {
          return acc.concat(cur);
        } else {
          return acc.concat([cur]);
        }
      })
    });

    return flatten;
  }
}
