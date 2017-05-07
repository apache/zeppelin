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

export function isInputWidget (spec) { return spec.widget === Widget.INPUT }
export function isOptionWidget (spec) { return spec.widget === Widget.OPTION }
export function isCheckboxWidget (spec) { return spec.widget === Widget.CHECKBOX }
export function isTextareaWidget (spec) { return spec.widget === Widget.TEXTAREA }
export function isBtnGroupWidget (spec) { return spec.widget === Widget.BTN_GROUP }

export function resetTableOptionConfig(config) {
  delete config.tableOptionSpecHash
  delete config.tableGridState
  delete config.tableOptionValue
  return config
}

export function initializeTableConfig(config, tableOptionSpecs) {
  if (typeof config === 'undefined') { config = {} }
  if (typeof config.tableOptionValue === 'undefined') { config.tableOptionValue = {} }
  if (typeof config.tableGridState === 'undefined') { config.tableGridState = {} }

  let specsUpdated = false

  // should remove `$$hashKey` using angular.toJson
  const newSpecHash = JSON.stringify(JSON.parse(angular.toJson(tableOptionSpecs)))
  const previousSpecHash = config.tableOptionSpecHash

  // check whether spec is updated or not
  if (typeof previousSpecHash === 'undefined' || (previousSpecHash !== newSpecHash)) {
    specsUpdated = true
    config.tableOptionSpecHash = newSpecHash
    config.initialized = true
    config.tableGridState = {}
  }

  // reset all persisted option values if spec is updated
  if (specsUpdated) {
    config.tableOptionValue = {}
    for (let i = 0; i < tableOptionSpecs.length; i++) {
      const option = tableOptionSpecs[i]
      config.tableOptionValue[option.name] = option.defaultValue
    }
    config.initialized = true
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
