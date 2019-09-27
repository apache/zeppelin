<template>
  <div v-show="!isOutputHidden" class="results mt-4">
    <pre v-if="isText" class="text-paragraph">{{getTextResult}}</pre>
    <div v-if="isHTML && !isForceEditorShow" v-on:dblclick="dbClickMdPara()" class="md-paragraph" v-html="getTextResult"></div>
    <div v-if="isGraph" :id="'chart-container-' + paragraph.id"><svg :id="'svg-' + paragraph.id"></svg></div>
    <div v-if="isTable" class="table-results">
      <table class="table table-sm">
        <thead>
          <tr>
            <th :key="columnName.index" v-for="columnName in generateTableData.columnNames">{{columnName.name}}</th>
          </tr>
        </thead>
        <tbody>
          <tr :key="index" v-for="(row, index) in generateTableData.rows">
            <td :key="index" v-for="(rowData, index) in row">{{rowData}}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script>
import d3 from 'd3'
import nv from 'nvd3'

export default {
  name: 'Results',
  components: {

  },
  props: ['result', 'index', 'paragraph', 'noteId'],
  data () {
    return {
      'chart': []
    }
  },
  computed: {
    isOutputHidden: function () {
      const { id } = this.$props.paragraph
      const paragraph = this.$store.getters.getParagraphById(id, this.$props.noteId)

      if (paragraph.result && paragraph.result.type.toLowerCase() === 'html') {
        return false
      }

      if (paragraph && paragraph.config) {
        return paragraph.config.tableHide
      }

      return false
    },
    isText: function () {
      const { type } = this.$props.result
      if (type.toLowerCase() === 'text') {
        return true
      }

      return false
    },
    isGraph: function () {
      const { id } = this.$props.paragraph
      const paragraph = this.$store.getters.getParagraphById(id, this.$props.noteId)

      if (paragraph.result && paragraph.result.type.toLowerCase() === 'table' && paragraph.config.graph.mode.toLowerCase() !== 'table') {
        return true
      }

      return false
    },
    isTable: function () {
      const { type } = this.$props.result

      if (type.toLowerCase() === 'table') {
        const index = this.$props.index
        const { graph } = this.$props.paragraph.config.results[index]

        if (graph.mode.toLowerCase() === 'table') {
          return true
        }
      }

      return false
    },
    isHTML: function () {
      const { type } = this.$props.result
      if (type.toLowerCase() === 'html') {
        return true
      }

      return false
    },
    isForceEditorShow: function () {
      const { id } = this.$props.paragraph
      const paragraph = this.$store.getters.getParagraphById(id, this.$props.noteId)

      if (paragraph && paragraph.forceEditorShow) {
        return true
      }

      return false
    },
    getTextResult: function () {
      const { id } = this.$props.paragraph
      const paragraph = this.$store.getters.getParagraphById(id, this.$props.noteId)
      let newResult = paragraph.results.msg[0].data

      // trim %md
      if (this.isHTML) {
        newResult = newResult.replace('<p>%md</p>', '')
      }

      return newResult
    },
    generateTableData: function () {
      const index = this.$props.index
      const { graph } = this.$props.paragraph.config.results[index]
      const { data } = this.$props.result

      let colNames = []

      colNames = graph.keys

      // process column names
      for (let i = 0; i < graph.values.length; i++) {
        colNames.push(graph.values[i])
      }

      // now process table results
      let rows = data.split('\n')
      let rowData = []

      for (let j = 0; j < rows.length; j++) {
        rowData.push(rows[j].split('\t'))
      }

      return {
        columnNames: colNames,
        rows: rowData
      }
    }
  },
  methods: {
    arrEq: function (a, b) {
      if (a === b) return true
      if (a == null || b == null) return false
      if (a.length !== b.length) return false

      for (let i = 0; i < a.length; ++i) {
        if (a[i] !== b[i]) return false
      }

      return true
    },
    clearUnknownColsFromGraphOption: function (result) {
      let columnNames = result.columnNames

      let unique = function (list) {
        for (let i = 0; i < list.length; i++) {
          for (let j = i + 1; j < list.length; j++) {
            if (this.arrEq(list[i], list[j])) {
              list.splice(j, 1)
            }
          }
        }
      }

      let removeUnknown = function (list) {
        for (let i = 0; i < list.length; i++) {
          // remove non existing column
          let found = false
          for (let j = 0; j < columnNames.length; j++) {
            let a = list[i]
            let b = columnNames[j]
            if (a.index === b.index && a.name === b.name) {
              found = true
              break
            }
          }
          if (!found) {
            list.splice(i, 1)
          }
        }
      }

      let removeUnknownFromScatterSetting = function (fields) {
        for (let f in fields) {
          if (fields[f]) {
            let found = false
            for (let i = 0; i < columnNames.length; i++) {
              let a = fields[f]
              let b = columnNames[i]
              if (a.index === b.index && a.name === b.name) {
                found = true
                break
              }
            }
            if (!found) {
              fields[f] = null
            }
          }
        }
      }

      unique(this.$props.paragraph.config.graph.keys)
      removeUnknown(this.$props.paragraph.config.graph.keys)

      removeUnknown(this.$props.paragraph.config.graph.values)

      unique(this.$props.paragraph.config.graph.groups)
      removeUnknown(this.$props.paragraph.config.graph.groups)

      removeUnknownFromScatterSetting(this.$props.paragraph.config.graph.scatter)
    },
    selectDefaultColsForGraphOption: function () {
      if (this.$props.paragraph.config.graph.keys.length === 0 && this.$props.paragraph.result.columnNames.length > 0) {
        this.$props.paragraph.config.graph.keys.push(this.$props.paragraph.result.columnNames[0])
      }

      if (this.$props.paragraph.config.graph.values.length === 0 && this.$props.paragraph.result.columnNames.length > 1) {
        this.$props.paragraph.config.graph.values.push(this.$props.paragraph.result.columnNames[1])
      }

      if (!this.$props.paragraph.config.graph.scatter.xAxis && !this.$props.paragraph.config.graph.scatter.yAxis) {
        if (this.$props.paragraph.result.columnNames.length > 1) {
          this.$props.paragraph.config.graph.scatter.xAxis = this.$props.paragraph.result.columnNames[0]
          this.$props.paragraph.config.graph.scatter.yAxis = this.$props.paragraph.result.columnNames[1]
        } else if (this.$props.paragraph.result.columnNames.length === 1) {
          this.$props.paragraph.config.graph.scatter.xAxis = this.$props.paragraph.result.columnNames[0]
        }
      }
    },
    isValidNumber: function (num) {
      return num !== undefined && !isNaN(num)
    },
    isDiscrete: function (field) {
      let getUnique = function (f) {
        let uniqObj = {}
        let uniqArr = []
        let j = 0
        for (let i = 0; i < f.length; i++) {
          let item = f[i]
          if (uniqObj[item] !== 1) {
            uniqObj[item] = 1
            uniqArr[j++] = item
          }
        }
        return uniqArr
      }

      for (let i = 0; i < field.length; i++) {
        if (isNaN(parseFloat(field[i])) &&
            (typeof field[i] === 'string' || field[i] instanceof String)) {
          return true
        }
      }

      let threshold = 0.05
      let unique = getUnique(field)
      if (unique.length / field.length < threshold) {
        return true
      } else {
        return false
      }
    },
    setDiscreteScatterData: function (data) {
      let { xAxis, yAxis, group } = this.$props.paragraph.config.graph.scatter

      let xValue
      let yValue
      let grp

      let rows = {}

      for (let i = 0; i < data.rows.length; i++) {
        let row = data.rows[i]
        if (xAxis) {
          xValue = row[xAxis.index]
        }
        if (yAxis) {
          yValue = row[yAxis.index]
        }
        if (group) {
          grp = row[group.index]
        }

        let key = xValue + ',' + yValue + ',' + grp

        if (!rows[key]) {
          rows[key] = {
            x: xValue,
            y: yValue,
            group: grp,
            size: 1
          }
        } else {
          rows[key].size++
        }
      }

      // change object into array
      let newRows = []
      for (let r in rows) {
        let newRow = []
        if (xAxis) { newRow[xAxis.index] = rows[r].x }
        if (yAxis) { newRow[yAxis.index] = rows[r].y }
        if (group) { newRow[group.index] = rows[r].group }
        newRow[data.rows[0].length] = rows[r].size
        newRows.push(newRow)
      }
      return newRows
    },
    setScatterChart: function (data, refresh) {
      let { xAxis, yAxis, group, size } = this.$props.paragraph.config.graph.scatter

      let xValues = []
      let yValues = []
      let rows = {}
      let d3g = []

      let rowNameIndex = {}
      let colNameIndex = {}
      let grpNameIndex = {}
      let rowIndexValue = {}
      let colIndexValue = {}
      let grpIndexValue = {}
      let rowIdx = 0
      let colIdx = 0
      let grpIdx = 0
      let grpName = ''

      let xValue
      let yValue
      let row

      if (!xAxis && !yAxis) {
        return {
          d3g: []
        }
      }

      for (let i = 0; i < data.rows.length; i++) {
        row = data.rows[i]
        if (xAxis) {
          xValue = row[xAxis.index]
          xValues[i] = xValue
        }
        if (yAxis) {
          yValue = row[yAxis.index]
          yValues[i] = yValue
        }
      }

      let isAllDiscrete = ((xAxis && yAxis && this.isDiscrete(xValues) && this.isDiscrete(yValues)) ||
                          (!xAxis && this.isDiscrete(yValues)) ||
                          (!yAxis && this.isDiscrete(xValues)))

      if (isAllDiscrete) {
        rows = this.setDiscreteScatterData(data)
      } else {
        rows = data.rows
      }

      if (!group && isAllDiscrete) {
        grpName = 'count'
      } else if (!group && !size) {
        if (xAxis && yAxis) {
          grpName = '(' + xAxis.name + ', ' + yAxis.name + ')'
        } else if (xAxis && !yAxis) {
          grpName = xAxis.name
        } else if (!xAxis && yAxis) {
          grpName = yAxis.name
        }
      } else if (!group && size) {
        grpName = size.name
      }

      for (let i = 0; i < rows.length; i++) {
        row = rows[i]
        if (xAxis) {
          xValue = row[xAxis.index]
        }
        if (yAxis) {
          yValue = row[yAxis.index]
        }
        if (group) {
          grpName = row[group.index]
        }
        let sz = (isAllDiscrete) ? row[row.length - 1] : ((size) ? row[size.index] : 1)

        if (grpNameIndex[grpName] === undefined) {
          grpIndexValue[grpIdx] = grpName
          grpNameIndex[grpName] = grpIdx++
        }

        if (xAxis && rowNameIndex[xValue] === undefined) {
          rowIndexValue[rowIdx] = xValue
          rowNameIndex[xValue] = rowIdx++
        }

        if (yAxis && colNameIndex[yValue] === undefined) {
          colIndexValue[colIdx] = yValue
          colNameIndex[yValue] = colIdx++
        }

        if (!d3g[grpNameIndex[grpName]]) {
          d3g[grpNameIndex[grpName]] = {
            key: grpName,
            values: []
          }
        }

        d3g[grpNameIndex[grpName]].values.push({
          x: xAxis ? (isNaN(xValue) ? rowNameIndex[xValue] : parseFloat(xValue)) : 0,
          y: yAxis ? (isNaN(yValue) ? colNameIndex[yValue] : parseFloat(yValue)) : 0,
          size: isNaN(parseFloat(sz)) ? 1 : parseFloat(sz)
        })
      }

      return {
        xLabels: rowIndexValue,
        yLabels: colIndexValue,
        d3g: d3g
      }
    },
    pivotDataToD3ChartFormat: function (data, allowTextXAxis, fillMissingValues, chartType) {
      // construct d3 data
      let d3g = []

      let schema = data.schema
      let rows = data.rows
      let values = this.$props.paragraph.config.graph.values

      let concat = function (o, n) {
        if (!o) {
          return n
        } else {
          return o + '.' + n
        }
      }

      let getSchemaUnderKey = function (key, s) {
        for (let c in key.children) {
          s[c] = {}
          getSchemaUnderKey(key.children[c], s[c])
        }
      }

      let traverse = function (sKey, s, rKey, r, func, rowName, rowValue, colName) {
        if (s.type === 'key') {
          rowName = concat(rowName, sKey)
          rowValue = concat(rowValue, rKey)
        } else if (s.type === 'group') {
          colName = concat(colName, rKey)
        } else if ((s.type === 'value' && sKey === rKey) || valueOnly) {
          colName = concat(colName, rKey)
          func(rowName, rowValue, colName, r)
        }

        for (let c in s.children) {
          if (fillMissingValues && s.children[c].type === 'group' && r[c] === undefined) {
            let cs = {}
            getSchemaUnderKey(s.children[c], cs)
            traverse(c, s.children[c], c, cs, func, rowName, rowValue, colName)
            continue
          }

          for (let j in r) {
            if (s.children[c].type === 'key' || c === j) {
              traverse(c, s.children[c], j, r[j], func, rowName, rowValue, colName)
            }
          }
        }
      }

      let keys = this.$props.paragraph.config.graph.keys
      let groups = this.$props.paragraph.config.graph.groups
      values = this.$props.paragraph.config.graph.values
      let valueOnly = (keys.length === 0 && groups.length === 0 && values.length > 0)
      let noKey = (keys.length === 0)
      let isMultiBarChart = (chartType === 'multiBarChart')

      let sKey = Object.keys(schema)[0]

      let rowNameIndex = {}
      let rowIdx = 0
      let colNameIndex = {}
      let colIdx = 0
      let rowIndexValue = {}

      for (let k in rows) {
        traverse(sKey, schema[sKey], k, rows[k], function (rowName, rowValue, colName, value) {
          if (rowNameIndex[rowValue] === undefined) {
            rowIndexValue[rowIdx] = rowValue
            rowNameIndex[rowValue] = rowIdx++
          }

          if (colNameIndex[colName] === undefined) {
            colNameIndex[colName] = colIdx++
          }
          let i = colNameIndex[colName]
          if (noKey && isMultiBarChart) {
            i = 0
          }

          if (!d3g[i]) {
            d3g[i] = {
              values: [],
              key: (noKey && isMultiBarChart) ? 'values' : colName
            }
          }

          let xVar = isNaN(rowValue) ? ((allowTextXAxis) ? rowValue : rowNameIndex[rowValue]) : parseFloat(rowValue)
          let yVar = 0
          if (xVar === undefined) { xVar = colName }
          if (value !== undefined) {
            yVar = isNaN(value.value) ? 0 : parseFloat(value.value) / parseFloat(value.count)
          }
          d3g[i].values.push({
            x: xVar,
            y: yVar
          })
        })
      }

      // clear aggregation name, if possible
      let namesWithoutAggr = {}
      let colName
      let withoutAggr
      // TODO - This part could use som refactoring - Weird if/else with similar actions and variable names
      for (colName in colNameIndex) {
        withoutAggr = colName.substring(0, colName.lastIndexOf('('))
        if (!namesWithoutAggr[withoutAggr]) {
          namesWithoutAggr[withoutAggr] = 1
        } else {
          namesWithoutAggr[withoutAggr]++
        }
      }

      if (valueOnly) {
        for (let valueIndex = 0; valueIndex < d3g[0].values.length; valueIndex++) {
          colName = d3g[0].values[valueIndex].x
          if (!colName) {
            continue
          }

          withoutAggr = colName.substring(0, colName.lastIndexOf('('))
          if (namesWithoutAggr[withoutAggr] <= 1) {
            d3g[0].values[valueIndex].x = withoutAggr
          }
        }
      } else {
        for (let d3gIndex = 0; d3gIndex < d3g.length; d3gIndex++) {
          colName = d3g[d3gIndex].key
          withoutAggr = colName.substring(0, colName.lastIndexOf('('))
          if (namesWithoutAggr[withoutAggr] <= 1) {
            d3g[d3gIndex].key = withoutAggr
          }
        }

        // use group name instead of group.value as a column name, if there're only one group and one value selected.
        if (groups.length === 1 && values.length === 1) {
          for (let d3gIndex = 0; d3gIndex < d3g.length; d3gIndex++) {
            colName = d3g[d3gIndex].key
            colName = colName.split('.').slice(0, -1).join('.')
            d3g[d3gIndex].key = colName
          }
        }
      }

      return {
        xLabels: rowIndexValue,
        d3g: d3g
      }
    },
    customAbbrevFormatter: function (x) {
      let s = d3.format('.3s')(x)
      switch (s[s.length - 1]) {
        case 'G': return s.slice(0, -1) + 'B'
      }
      return s
    },
    xAxisTickFormat: function (d, xLabels) {
      if (xLabels[d] && (isNaN(parseFloat(xLabels[d])) || !isFinite(xLabels[d]))) {
        return xLabels[d]
      } else {
        return d
      }
    },
    yAxisTickFormat: function (d) {
      if (Math.abs(d) >= Math.pow(10, 6)) {
        return this.customAbbrevFormatter(d)
      }
      // todo check here
      return d3.format(',')(d3.round(d, 3))
    },
    pivot: function (data) {
      let keys = this.$props.paragraph.config.graph.keys
      let groups = this.$props.paragraph.config.graph.groups
      let values = this.$props.paragraph.config.graph.values

      let aggrFunc = {
        sum: function (a, b) {
          let varA = (a !== undefined) ? (isNaN(a) ? 0 : parseFloat(a)) : 0
          let varB = (b !== undefined) ? (isNaN(b) ? 0 : parseFloat(b)) : 0
          return varA + varB
        },
        count: function (a, b) {
          let varA = (a !== undefined) ? parseInt(a) : 0
          let varB = (b !== undefined) ? 1 : 0
          return varA + varB
        },
        min: function (a, b) {
          let aIsValid = this.isValidNumber(a)
          let bIsValid = this.isValidNumber(b)
          if (!aIsValid) {
            return parseFloat(b)
          } else if (!bIsValid) {
            return parseFloat(a)
          } else {
            return Math.min(parseFloat(a), parseFloat(b))
          }
        },
        max: function (a, b) {
          let aIsValid = this.isValidNumber(a)
          let bIsValid = this.isValidNumber(b)
          if (!aIsValid) {
            return parseFloat(b)
          } else if (!bIsValid) {
            return parseFloat(a)
          } else {
            return Math.max(parseFloat(a), parseFloat(b))
          }
        },
        avg: function (a, b, c) {
          let varA = (a !== undefined) ? (isNaN(a) ? 0 : parseFloat(a)) : 0
          let varB = (b !== undefined) ? (isNaN(b) ? 0 : parseFloat(b)) : 0
          return varA + varB
        }
      }

      let aggrFuncDiv = {
        sum: false,
        count: false,
        min: false,
        max: false,
        avg: true
      }

      let schema = {}
      let rows = {}

      for (let i = 0; i < data.rows.length; i++) {
        let row = data.rows[i]
        // let newRow = {}
        let s = schema
        let p = rows

        for (let k = 0; k < keys.length; k++) {
          let key = keys[k]

          // add key to schema
          if (!s[key.name]) {
            s[key.name] = {
              order: k,
              index: key.index,
              type: 'key',
              children: {}
            }
          }
          s = s[key.name].children

          // add key to row
          let keyKey = row[key.index]
          if (!p[keyKey]) {
            p[keyKey] = {}
          }
          p = p[keyKey]
        }

        for (let g = 0; g < groups.length; g++) {
          let group = groups[g]
          let groupKey = row[group.index]

          // add group to schema
          if (!s[groupKey]) {
            s[groupKey] = {
              order: g,
              index: group.index,
              type: 'group',
              children: {}
            }
          }
          s = s[groupKey].children

          // add key to row
          if (!p[groupKey]) {
            p[groupKey] = {}
          }
          p = p[groupKey]
        }

        for (let v = 0; v < values.length; v++) {
          let value = values[v]
          let valueKey = value.name + '(' + value.aggr + ')'

          // add value to schema
          if (!s[valueKey]) {
            s[valueKey] = {
              type: 'value',
              order: v,
              index: value.index
            }
          }

          // add value to row
          if (!p[valueKey]) {
            p[valueKey] = {
              value: (value.aggr !== 'count') ? row[value.index] : 1,
              count: 1
            }
          } else {
            p[valueKey] = {
              value: aggrFunc[value.aggr](p[valueKey].value, row[value.index], p[valueKey].count + 1),
              count: (aggrFuncDiv[value.aggr]) ? p[valueKey].count + 1 : p[valueKey].count
            }
          }
        }
      }

      return {
        schema: schema,
        rows: rows
      }
    },
    setD3Chart: function (type, data, refresh) {
      let _this = this
      if (!this.chart[type]) {
        let chart = nv.models[type]()
        this.chart[type] = chart
      }

      let d3g = []
      let xLabels
      let yLabels

      if (type === 'scatterChart') {
        let scatterData = this.setScatterChart(data, refresh)

        xLabels = scatterData.xLabels
        yLabels = scatterData.yLabels
        d3g = scatterData.d3g

        this.chart[type].xAxis.tickFormat(function (d) { return _this.xAxisTickFormat(d, xLabels) })
        this.chart[type].yAxis.tickFormat(function (d) { return _this.yAxisTickFormat(d, yLabels) })
        this.chart[type].showLabels(false)
        this.chart[type].showDistX(true)
          .showDistY(true)
      } else {
        let p = this.pivot(data)
        if (type === 'pieChart') {
          let d = this.pivotDataToD3ChartFormat(p, true).d3g

          this.chart[type].x(function (d) { return d.label })
            .y(function (d) { return d.value })

          if (d.length > 0) {
            for (let i = 0; i < d[0].values.length; i++) {
              let e = d[0].values[i]
              d3g.push({
                label: e.x,
                value: e.y
              })
            }
          }
        } else if (type === 'multiBarChart') {
          d3g = this.pivotDataToD3ChartFormat(p, true, true, type).d3g
          this.chart[type].yAxis.axisLabelDistance(50)
          this.chart[type].yAxis.tickFormat(function (d) { return _this.yAxisTickFormat(d) })
        } else if (type === 'lineChart' || type === 'stackedAreaChart' || type === 'lineWithFocusChart') {
          let pivotdata = this.pivotDataToD3ChartFormat(p, false, true)
          xLabels = pivotdata.xLabels
          d3g = pivotdata.d3g
          this.chart[type].xAxis.tickFormat(function (d) { return _this.xAxisTickFormat(d, xLabels) })
          if (type === 'stackedAreaChart') {
            this.chart[type].yAxis.tickFormat(function (d) { return _this.yAxisTickFormat(d) })
          } else {
            this.chart[type].yAxis.tickFormat(function (d) { return _this.yAxisTickFormat(d, xLabels) })
          }
          this.chart[type].yAxis.axisLabelDistance(50)
          if (this.chart[type].useInteractiveGuideline) {
            this.chart[type].useInteractiveGuideline(true)
          }
          if (this.paragraph.config.graph.forceY) {
            this.chart[type].forceY([0])
          } else {
            this.chart[type].forceY([])
          }
        }
      }

      let renderChart = function (paragraph, chart) {
        if (!refresh) {
          // TODO force destroy previous chart
        }

        let height = paragraph.config.graph.height

        let animationDuration = 300
        let numberOfDataThreshold = 150
        // turn off animation when dataset is too large. (for performance issue)
        // still, since dataset is large, the chart content sequentially appears like animated.
        try {
          if (d3g[0].values.length > numberOfDataThreshold) {
            animationDuration = 0
          }
        } catch (ignoreErr) {
        }
        let width = document.getElementById('chart-container-' + paragraph.id).width
        d3.select('#svg-' + paragraph.id)
          .attr('viewBox', '0 0 ' + width + ' ' + paragraph.config.graph.height)
          .attr('preserveAspectRatio', 'xMinYMin meet')
          .datum(d3g)
          .transition()
          .duration(animationDuration)
          .call(chart[type])
        d3.select('#svg-' + paragraph.id).style.height = height + 'px'
        nv.utils.windowResize(chart[type].update)
      }

      renderChart(this.$props.paragraph, this.chart)
    },
    dbClickMdPara: function () {
      const { id } = this.$props.paragraph

      this.$store.dispatch('setParagraphProp', {
        id: id,
        noteId: this.$props.noteId,
        prop: {
          name: 'forceEditorShow',
          value: true
        }
      })
    }
  },
  mounted: function () {
    let _this = this
    setTimeout(function () {
      if (_this.isGraph) {
        _this.clearUnknownColsFromGraphOption(_this.generateTableData)
        _this.setD3Chart(_this.$props.paragraph.config.graph.mode, _this.generateTableData, false)
      }
    }, 2000)
  }
}
</script>

<style scoped>
  pre {
    font-size: 12px;
    border: none;
    margin: 0px;
    padding: 0px;
    overflow-x: auto;
    overflow-y: auto;
    word-break: break-all;
    word-wrap: break-word;
    white-space: pre-wrap;
    max-height: 250px;
  }

  #chart1, svg {
    margin: 0px;
    padding: 0px;
    height: 300px;
    width: 100%;
  }

  .table-results {
    overflow: auto;
    max-height: 300px;
  }

  .md-paragraph {
    font-size: 13px;
  }

  table {
    font-size: 13px;
  }
</style>
