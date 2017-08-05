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
  NETWORK: 'NETWORK'
}

export const DefaultDisplayMagic = {
  '%element': DefaultDisplayType.ELEMENT,
  '%table': DefaultDisplayType.TABLE,
  '%html': DefaultDisplayType.HTML,
  '%angular': DefaultDisplayType.ANGULAR,
  '%text': DefaultDisplayType.TEXT,
  '%network': DefaultDisplayType.NETWORK,
}

export class DataWithType {
  constructor (data, type, magic, text) {
    this.data = data
    this.type = type

    /**
     * keep for `DefaultDisplayType.ELEMENT` (function data type)
     * to propagate a result to other client.
     *
     * otherwise we will send function as `data` and it will not work
     * since they don't have context where they are created.
     */

    this.magic = magic
    this.text = text
  }

  static handleDefaultMagic (m) {
    // let's use default display type instead of magic in case of default
    // to keep consistency with backend interpreter
    if (DefaultDisplayMagic[m]) {
      return DefaultDisplayMagic[m]
    } else {
      return m
    }
  }

  static createPropagable (dataWithType) {
    if (!SpellResult.isFunction(dataWithType.data)) {
      return dataWithType
    }

    const data = dataWithType.getText()
    const type = dataWithType.getMagic()

    return new DataWithType(data, type)
  }

  /**
   * consume 1 data and produce multiple
   * @param data {string}
   * @param customDisplayType
   * @return {Array<DataWithType>}
   */
  static parseStringData (data, customDisplayMagic) {
    function availableMagic (magic) {
      return magic && (DefaultDisplayMagic[magic] || customDisplayMagic[magic])
    }

    const splited = data.split('\n')

    const gensWithTypes = []
    let mergedGens = []
    let previousMagic = DefaultDisplayType.TEXT

    // create `DataWithType` whenever see available display type.
    for (let i = 0; i < splited.length; i++) {
      const g = splited[i]
      const magic = SpellResult.extractMagic(g)

      // create `DataWithType` only if see new magic
      if (availableMagic(magic) && mergedGens.length > 0) {
        gensWithTypes.push(new DataWithType(mergedGens.join(''), previousMagic))
        mergedGens = []
      }

      // accumulate `data` to mergedGens
      if (availableMagic(magic)) {
        const withoutMagic = g.split(magic)[1]
        mergedGens.push(`${withoutMagic}\n`)
        previousMagic = DataWithType.handleDefaultMagic(magic)
      } else {
        mergedGens.push(`${g}\n`)
      }
    }

    // cleanup the last `DataWithType`
    if (mergedGens.length > 0) {
      previousMagic = DataWithType.handleDefaultMagic(previousMagic)
      gensWithTypes.push(new DataWithType(mergedGens.join(''), previousMagic))
    }

    return gensWithTypes
  }

  /**
   * get 1 `DataWithType` and produce multiples using available displays
   * return an wrapped with a promise to generalize result output which can be
   * object, function or promise
   * @param dataWithType {DataWithType}
   * @param availableDisplays {Object} Map for available displays
   * @param magic
   * @param textWithoutMagic
   * @return {Promise<Array<DataWithType>>}
   */
  static produceMultipleData (dataWithType, customDisplayType,
                             magic, textWithoutMagic) {
    const data = dataWithType.getData()
    const type = dataWithType.getType()

    // if the type is specified, just return it
    // handle non-specified dataWithTypes only
    if (type) {
      return new Promise((resolve) => { resolve([dataWithType]) })
    }

    let wrapped

    if (SpellResult.isFunction(data)) {
      // if data is a function, we consider it as ELEMENT type.
      wrapped = new Promise((resolve) => {
        const dt = new DataWithType(
          data, DefaultDisplayType.ELEMENT, magic, textWithoutMagic)
        const result = [dt]
        return resolve(result)
      })
    } else if (SpellResult.isPromise(data)) {
      // if data is a promise,
      wrapped = data.then(generated => {
        const result =
          DataWithType.parseStringData(generated, customDisplayType)
        return result
      })
    } else {
      // if data is a object, parse it to multiples
      wrapped = new Promise((resolve) => {
        const result =
          DataWithType.parseStringData(data, customDisplayType)
        return resolve(result)
      })
    }

    return wrapped
  }

  /**
   * `data` can be promise, function or just object
   * - if data is an object, it will be used directly.
   * - if data is a function, it will be called with DOM element id
   *   where the final output is rendered.
   * - if data is a promise, the post processing logic
   *   will be called in `then()` of this promise.
   * @returns {*} `data` which can be object, function or promise.
   */
  getData () {
    return this.data
  }

  /**
   * Value of `type` might be empty which means
   * data can be separated into multiples
   * by `SpellResult.parseStringData()`
   * @returns {string}
   */
  getType () {
    return this.type
  }

  getMagic () {
    return this.magic
  }

  getText () {
    return this.text
  }
}

export class SpellResult {
  constructor (resultData, resultType) {
    this.dataWithTypes = []
    this.add(resultData, resultType)
  }

  static isFunction (data) {
    return (data && typeof data === 'function')
  }

  static isPromise (data) {
    return (data && typeof data.then === 'function')
  }

  static isObject (data) {
    return (data &&
      !SpellResult.isFunction(data) &&
      !SpellResult.isPromise(data))
  }

  static extractMagic (allParagraphText) {
    const pattern = /^\s*%(\S+)\s*/g
    try {
      let match = pattern.exec(allParagraphText)
      if (match) {
        return `%${match[1].trim()}`
      }
    } catch (error) {
      // failed to parse, ignore
    }

    return undefined
  }

  static createPropagable (resultMsg) {
    return resultMsg.map(dt => {
      return DataWithType.createPropagable(dt)
    })
  }

  add (resultData, resultType) {
    if (resultData) {
      this.dataWithTypes.push(
        new DataWithType(resultData, resultType))
    }

    return this
  }

  /**
   * @param customDisplayType
   * @param textWithoutMagic
   * @return {Promise<Array<DataWithType>>}
   */
  getAllParsedDataWithTypes (customDisplayType, magic, textWithoutMagic) {
    const promises = this.dataWithTypes.map(dt => {
      return DataWithType.produceMultipleData(
        dt, customDisplayType, magic, textWithoutMagic)
    })

    // some promises can include an array so we need to flatten them
    const flatten = Promise.all(promises).then(values => {
      return values.reduce((acc, cur) => {
        if (Array.isArray(cur)) {
          return acc.concat(cur)
        } else {
          return acc.concat([cur])
        }
      })
    })

    return flatten
  }
}
