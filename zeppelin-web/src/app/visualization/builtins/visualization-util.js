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
  delete config.tableOptionSpec
  return config
}

export function initializeTableConfig(config, tableOptionSpecs) {
  if (typeof config === 'undefined') { config = {} }
  if (typeof config.tableOptionValue === 'undefined') { config.tableOptionValue = {} }

  let specsUpdated = false

  // should remove `$$hashKey` using angular.toJson
  const newSpecHash = JSON.stringify(JSON.parse(angular.toJson(tableOptionSpecs)))
  const previousSpecHash = config.tableOptionSpecHash

  // check whether spec is updated or not
  if (typeof previousSpecHash === 'undefined' || (previousSpecHash !== newSpecHash)) {
    specsUpdated = true
    config.tableOptionSpecHash = newSpecHash
    config.initialized = true
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
