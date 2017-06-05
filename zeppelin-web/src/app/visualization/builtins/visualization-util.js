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

export const Widget = {
  CHECKBOX: 'checkbox',
  INPUT: 'input',
  TEXTAREA: 'textarea',
  OPTION: 'option',
  BTN_GROUP: 'btn-group',
}

export const ValueType = {
  INT: 'int',
  FLOAT: 'float',
  BOOLEAN: 'boolean',
  STRING: 'string',
  JSON: 'JSON',
}

export const TableColumnType = {
  STRING: 'string',
  BOOLEAN: 'boolean',
  NUMBER: 'number',
  DATE: 'date',
  OBJECT: 'object',
  NUMBER_STR: 'numberStr',
}

export const DefaultTableColumnType = TableColumnType.STRING

export function isInputWidget (spec) { return spec.widget === Widget.INPUT }
export function isOptionWidget (spec) { return spec.widget === Widget.OPTION }
export function isCheckboxWidget (spec) { return spec.widget === Widget.CHECKBOX }
export function isTextareaWidget (spec) { return spec.widget === Widget.TEXTAREA }
export function isBtnGroupWidget (spec) { return spec.widget === Widget.BTN_GROUP }

export function resetTableOptionConfig(config) {
  delete config.tableOptionSpecHash
  config.tableOptionSpecHash = {}
  delete config.tableOptionValue
  config.tableOptionValue = {}
  delete config.tableColumnTypeState.names
  config.tableColumnTypeState.names = {}
  config.updated = false
  return config
}

export function initializeTableConfig(config, tableOptionSpecs) {
  if (typeof config.tableOptionValue === 'undefined') { config.tableOptionValue = {} }
  if (typeof config.tableGridState === 'undefined') { config.tableGridState = {} }
  if (typeof config.tableColumnTypeState === 'undefined') { config.tableColumnTypeState = {} }

  // should remove `$$hashKey` using angular.toJson
  const newSpecHash = JSON.stringify(JSON.parse(angular.toJson(tableOptionSpecs)))
  const previousSpecHash = config.tableOptionSpecHash

  // check whether spec is updated or not
  if (typeof previousSpecHash === 'undefined' || (previousSpecHash !== newSpecHash)) {
    resetTableOptionConfig(config)

    config.tableOptionSpecHash = newSpecHash
    config.initialized = true

    // reset all persisted option values if spec is updated
    for (let i = 0; i < tableOptionSpecs.length; i++) {
      const option = tableOptionSpecs[i]
      config.tableOptionValue[option.name] = option.defaultValue
    }
  }

  return config
}

export function parseTableOption(specs, persistedTableOption) {
  /** copy original params */
  const parsed = JSON.parse(JSON.stringify(persistedTableOption))

  for (let i = 0; i < specs.length; i++) {
    const s = specs[i]
    const name = s.name

    if (s.valueType === ValueType.INT &&
      typeof parsed[name] !== 'number') {
      try { parsed[name] = parseInt(parsed[name]) } catch (error) { parsed[name] = s.defaultValue }
    } else if (s.valueType === ValueType.FLOAT &&
      typeof parsed[name] !== 'number') {
      try { parsed[name] = parseFloat(parsed[name]) } catch (error) { parsed[name] = s.defaultValue }
    } else if (s.valueType === ValueType.BOOLEAN) {
      if (parsed[name] === 'false') {
        parsed[name] = false
      } else if (parsed[name] === 'true') {
        parsed[name] = true
      } else if (typeof parsed[name] !== 'boolean') {
        parsed[name] = s.defaultValue
      }
    } else if (s.valueType === ValueType.JSON) {
      if (parsed[name] !== null && typeof parsed[name] !== 'object') {
        try { parsed[name] = JSON.parse(parsed[name]) } catch (error) { parsed[name] = s.defaultValue }
      } else if (parsed[name] === null) {
        parsed[name] = s.defaultValue
      }
    }
  }

  return parsed
}

export function isColumnNameUpdated(prevColumnNames, newColumnNames) {
  if (typeof prevColumnNames === 'undefined') { return true }

  let columnNameUpdated = false

  for (let prevColName in prevColumnNames) {
    if (!newColumnNames[prevColName]) {
      return true
    }
  }

  if (!columnNameUpdated) {
    for (let newColName in newColumnNames) {
      if (!prevColumnNames[newColName]) {
        return true
      }
    }
  }

  return false
}

export function updateColumnTypeState(columns, config, columnDefs) {
  const columnTypeState = config.tableColumnTypeState

  if (!columnTypeState) { return }

  // compare objects because order might be changed
  const prevColumnNames = columnTypeState.names || {}
  const newColumnNames = columns.reduce((acc, c) => {
    const prevColumnType = prevColumnNames[c.name]

    // use previous column type if exists
    if (prevColumnType) {
      acc[c.name] = prevColumnType
    } else {
      acc[c.name] = DefaultTableColumnType
    }
    return acc
  }, {})

  let columnNameUpdated = isColumnNameUpdated(prevColumnNames, newColumnNames)

  if (columnNameUpdated) {
    columnTypeState.names = newColumnNames
    columnTypeState.updated = true
  }

  // update `columnDefs[n].type`
  for (let i = 0; i < columnDefs.length; i++) {
    const colName = columnDefs[i].name
    columnDefs[i].type = columnTypeState.names[colName]
  }
}
