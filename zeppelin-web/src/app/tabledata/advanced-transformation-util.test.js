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

import * as Util from './advanced-transformation-util.js'

const MockParameter = {
  'floatParam': { valueType: 'float', defaultValue: 10, description: '', },
  'intParam': { valueType: 'int', defaultValue: 50, description: '', },
  'jsonParam': { valueType: 'JSON', defaultValue: '', description: '', widget: 'textarea', },
  'stringParam1': { valueType: 'string', defaultValue: '', description: '', },
  'stringParam2': { valueType: 'string', defaultValue: '', description: '', widget: 'input', },
  'boolParam': { valueType: 'boolean', defaultValue: false, description: '', widget: 'checkbox', },
  'optionParam': { valueType: 'string', defaultValue: 'line', description: '', widget: 'option', optionValues: [ 'line', 'smoothedLine', ], },
}

const MockAxis1 = {
  'keyAxis': { dimension: 'multiple', axisType: 'key', },
  'aggrAxis': { dimension: 'multiple', axisType: 'aggregator', },
  'groupAxis': { dimension: 'multiple', axisType: 'group', },
}

const MockAxis2 = {
  'singleKeyAxis': { dimension: 'single', axisType: 'key', },
  'limitedAggrAxis': { dimension: 'multiple', axisType: 'aggregator', maxAxisCount: 2, },
  'singleGroupAxis': { dimension: 'single', axisType: 'group', },
}

const MockAxis3 = {
  'customAxis1': { dimension: 'single', axisType: 'unique', },
  'customAxis2': { dimension: 'multiple', axisType: 'value', },
}

const MockSpec = {
  charts: {
    'object-chart': {
      transform: { method: 'object', },
      sharedAxis: true,
      axis: MockAxis1,
      parameter: MockParameter,
    },

    'array-chart': {
      transform: { method: 'array', },
      sharedAxis: true,
      axis: MockAxis1,
      parameter: {
        'arrayChartParam0': { valueType: 'string', defaultValue: '', description: 'param0', },
      },
    },

    'drillDown-chart': {
      transform: { method: 'drill-down', },
      axis: MockAxis2,
      parameter: {
        'drillDownChartParam0': { valueType: 'string', defaultValue: '', description: 'param0', },
      },
    },

    'raw-chart': {
      transform: { method: 'raw', },
      axis: MockAxis3,
      parameter: {
        'rawChartParam0': { valueType: 'string', defaultValue: '', description: 'param0', },
      },
    },
  },
}


describe('advanced-transformation-util', () => {
  describe('getCurrent* funcs', () => {
    it('should set return proper value of the current chart', () => {
      const config  = {}
      Util.initializeConfig(config, MockSpec)
      expect(Util.getCurrentChart(config)).toEqual('object-chart')
      expect(Util.getCurrentChartTransform(config)).toEqual({method: 'object'})
      // use `toBe` to compare reference
      expect(Util.getCurrentChartAxis(config)).toBe(config.axis['object-chart'])
      // use `toBe` to compare reference
      expect(Util.getCurrentChartParam(config)).toBe(config.parameter['object-chart'])
    })
  })

  describe('useSharedAxis', () => {
    it('should set chartChanged for initial drawing', () => {
      const config  = {}
      Util.initializeConfig(config, MockSpec)
      expect(Util.useSharedAxis(config, 'object-chart')).toEqual(true)
      expect(Util.useSharedAxis(config, 'array-chart')).toEqual(true)
      expect(Util.useSharedAxis(config, 'drillDown-chart')).toBeUndefined()
      expect(Util.useSharedAxis(config, 'raw-chart')).toBeUndefined()
    })
  })

  describe('initializeConfig', () => {
    const config  = {}
    Util.initializeConfig(config, MockSpec)

    it('should set chartChanged for initial drawing', () => {
      expect(config.chartChanged).toBe(true)
      expect(config.parameterChanged).toBe(false)
    })

    it('should set panel toggles ', () => {
      expect(config.panel.columnPanelOpened).toBe(true)
      expect(config.panel.parameterPanelOpened).toBe(false)
    })

    it('should set version and initialized', () => {
      expect(config.spec.version).toBeDefined()
      expect(config.spec.initialized).toBe(true)
    })

    it('should set chart', () => {
      expect(config.chart.current).toBe('object-chart')
      expect(config.chart.available).toEqual([
        'object-chart',
        'array-chart',
        'drillDown-chart',
        'raw-chart',
      ])
    })

    it('should set sharedAxis', () => {
      expect(config.sharedAxis).toEqual({
        keyAxis: [], aggrAxis: [], groupAxis: [],
      })
      // should use `toBe` to compare object reference
      expect(config.sharedAxis).toBe(config.axis['object-chart'])
      // should use `toBe` to compare object reference
      expect(config.sharedAxis).toBe(config.axis['array-chart'])
    })

    it('should set paramSpecs', () => {
      const expected = Util.getSpecs(MockParameter)
      expect(config.paramSpecs['object-chart']).toEqual(expected)
      expect(config.paramSpecs['array-chart'].length).toEqual(1)
      expect(config.paramSpecs['drillDown-chart'].length).toEqual(1)
      expect(config.paramSpecs['raw-chart'].length).toEqual(1)
    })

    it('should set parameter with default value', () => {
      expect(Object.keys(MockParameter).length).toBeGreaterThan(0) // length > 0
      for (let paramName in MockParameter) {
        expect(config.parameter['object-chart'][paramName])
          .toEqual(MockParameter[paramName].defaultValue)
      }
    })

    it('should set axisSpecs', () => {
      const expected = Util.getSpecs(MockAxis1)
      expect(config.axisSpecs['object-chart']).toEqual(expected)
      expect(config.axisSpecs['array-chart'].length).toEqual(3)
      expect(config.axisSpecs['drillDown-chart'].length).toEqual(3)
      expect(config.axisSpecs['raw-chart'].length).toEqual(2)
    })

    it('should prepare axis depending on dimension', () => {
      expect(config.axis['object-chart']).toEqual({
        keyAxis: [], aggrAxis: [], groupAxis: [],
      })
      expect(config.axis['array-chart']).toEqual({
        keyAxis: [], aggrAxis: [], groupAxis: [],
      })
      // it's ok not to set single dimension axis
      expect(config.axis['drillDown-chart']).toEqual({ limitedAggrAxis: [], })
      // it's ok not to set single dimension axis
      expect(config.axis['raw-chart']).toEqual({ customAxis2: [], })
    })

  })

  describe('axis', () => {

  })

  describe('parameter:widget', () => {
    it('isInputWidget', () => {
      expect(Util.isInputWidget(MockParameter.stringParam1)).toBe(true)
      expect(Util.isInputWidget(MockParameter.stringParam2)).toBe(true)

      expect(Util.isInputWidget(MockParameter.boolParam)).toBe(false)
      expect(Util.isInputWidget(MockParameter.jsonParam)).toBe(false)
      expect(Util.isInputWidget(MockParameter.optionParam)).toBe(false)
    })

    it('isOptionWidget', () => {
      expect(Util.isOptionWidget(MockParameter.optionParam)).toBe(true)

      expect(Util.isOptionWidget(MockParameter.stringParam1)).toBe(false)
      expect(Util.isOptionWidget(MockParameter.stringParam2)).toBe(false)
      expect(Util.isOptionWidget(MockParameter.boolParam)).toBe(false)
      expect(Util.isOptionWidget(MockParameter.jsonParam)).toBe(false)
    })

    it('isCheckboxWidget', () => {
      expect(Util.isCheckboxWidget(MockParameter.boolParam)).toBe(true)

      expect(Util.isCheckboxWidget(MockParameter.stringParam1)).toBe(false)
      expect(Util.isCheckboxWidget(MockParameter.stringParam2)).toBe(false)
      expect(Util.isCheckboxWidget(MockParameter.jsonParam)).toBe(false)
      expect(Util.isCheckboxWidget(MockParameter.optionParam)).toBe(false)
    })

    it('isTextareaWidget', () => {
      expect(Util.isTextareaWidget(MockParameter.jsonParam)).toBe(true)

      expect(Util.isTextareaWidget(MockParameter.stringParam1)).toBe(false)
      expect(Util.isTextareaWidget(MockParameter.stringParam2)).toBe(false)
      expect(Util.isTextareaWidget(MockParameter.boolParam)).toBe(false)
      expect(Util.isTextareaWidget(MockParameter.optionParam)).toBe(false)
    })
  })

  describe('parameter:parseParameter', () => {
    const paramSpec = Util.getSpecs(MockParameter)

    it('should parse number', () => {
      const params = { intParam: '3', }
      const parsed = Util.parseParameter(paramSpec, params)
      expect(parsed.intParam).toBe(3)
    })

    it('should parse float', () => {
      const params = { floatParam: '0.10', }
      const parsed = Util.parseParameter(paramSpec, params)
      expect(parsed.floatParam).toBe(0.10)
    })

    it('should parse boolean', () => {
      const params1 = { boolParam: 'true', }
      const parsed1 = Util.parseParameter(paramSpec, params1)
      expect(typeof parsed1.boolParam).toBe('boolean')
      expect(parsed1.boolParam).toBe(true)

      const params2 = { boolParam: 'false', }
      const parsed2 = Util.parseParameter(paramSpec, params2)
      expect(typeof parsed2.boolParam).toBe('boolean')
      expect(parsed2.boolParam).toBe(false)
    })

    it('should parse JSON', () => {
      const params = { jsonParam: '{ "a": 3 }', }
      const parsed = Util.parseParameter(paramSpec, params)
      expect(typeof parsed.jsonParam).toBe('object')
      expect(JSON.stringify(parsed.jsonParam)).toBe('{"a":3}')
    })

    it('should not parse string', () => {
      const params = { stringParam: 'example', }
      const parsed = Util.parseParameter(paramSpec, params)
      expect(typeof parsed.stringParam).toBe('string')
      expect(parsed.stringParam).toBe('example')
    })

  })

  describe('removeDuplicatedColumnsInMultiDimensionAxis', () => {
    const config = {}
    Util.initializeConfig(config, MockSpec)

    const addColumn = function(config, value) {
      const axis = Util.getCurrentChartAxis(config)['limitedAggrAxis']
      axis.push(value)
      const axisSpecs = Util.getCurrentChartAxisSpecs(config)
      Util.removeDuplicatedColumnsInMultiDimensionAxis(config, axisSpecs[1])
    }

    it('should remove duplicated axis names in config', () => {
      config.chart.current = 'drillDown-chart' // set non-sharedAxis chart
      addColumn(config, 'columnA')
      addColumn(config, 'columnA')
      addColumn(config, 'columnA')

      expect(Util.getCurrentChartAxis(config)['limitedAggrAxis']).toEqual([
        'columnA',
      ])
    })
  })

  describe('applyMaxAxisCount', () => {
    const config = {}
    Util.initializeConfig(config, MockSpec)

    const addColumn = function(config, value) {
      const axis = Util.getCurrentChartAxis(config)['limitedAggrAxis']
      axis.push(value)
      const axisSpecs = Util.getCurrentChartAxisSpecs(config)
      Util.applyMaxAxisCount(config, axisSpecs[1])
    }

    it('should remove duplicated axis names in config', () => {
      config.chart.current = 'drillDown-chart' // set non-sharedAxis chart
      const axis = Util.getCurrentChartAxis(config)['limitedAggrAxis']
      const axisSpec = Util.getCurrentChartAxisSpecs(config)[1] // limitedAggrAxis

      addColumn(config, 'columnA')
      addColumn(config, 'columnB')
      addColumn(config, 'columnC')
      addColumn(config, 'columnD')

      expect(Util.getCurrentChartAxis(config)['limitedAggrAxis']).toEqual([
        'columnC', 'columnD',
      ])
    })
  })

})

