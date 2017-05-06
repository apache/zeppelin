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

export function initializeTableConfig(config, tableOptionSpecs) {
  if (typeof config === 'undefined') { config = {} }
  if (typeof config.tableOptionValue === 'undefined') { config.tableOptionValue = {} }

  let specsUpdated = false

  // check whether spec is updated or not
  const newSpec = tableOptionSpecs
  const previousSpecs = config.tableOptionSpec
  if (typeof previousSpecs === 'undefined' ||
    (JSON.stringify(previousSpecs) !== JSON.stringify(newSpec))) {
    specsUpdated = true
    config.tableOptionSpec = newSpec
  }

  // reset all persisted option values if spec is updated
  if (specsUpdated) {
    config.tableOptionValue = {}
    for (let i = 0; i < newSpec.length; i++) {
      const option = newSpec[i]
      config.tableOptionValue[option.name] = option.defaultValue
    }
  }

  return config
}
