/*
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import Visualization from '../visualization'
import NetworkTransformation from '../../tabledata/network'

/**
 * Visualize data in network format
 */
export default class NetworkVisualization extends Visualization {
  constructor(targetEl, config) {
    super(targetEl, config)
    console.log('Init network viz')
    if (!config.properties) {
      config.properties = {}
    }
    if (!config.d3Graph) {
      config.d3Graph = {
        forceLayout: {
          timeout: 10000,
          charge: -120,
          linkDistance: 80,
        },
        zoom: {
          minScale: 1.3
        }
      }
    }
    this.targetEl.addClass('network')
    this.containerId = this.targetEl.prop('id')
    this.force = null
    this.svg = null
    this.$timeout = angular.injector(['ng']).get('$timeout')
    this.$interpolate = angular.injector(['ng']).get('$interpolate')
    this.transformation = new NetworkTransformation(config)
  }

  refresh() {
    console.log('refresh')
  }

  render(networkData) {
    if (!('graph' in networkData)) {
      console.log('graph not found')
      return
    }
    if (!networkData.isRendered) {
      networkData.isRendered = true
    } else {
      return
    }
    console.log('Rendering the graph')

    if (networkData.graph.edges.length &&
        !networkData.isDefaultSet) {
      networkData.isDefaultSet = true
      this._setEdgesDefaults(networkData.graph)
    }

    const transformationConfig = this.transformation.getSetting().scope.config
    console.log('cfg', transformationConfig)
    if (transformationConfig && angular.equals({}, transformationConfig.properties)) {
      transformationConfig.properties = this.getNetworkProperties(networkData.graph)
    }

    this.targetEl.empty().append('<svg></svg>')

    const width = this.targetEl.width()
    const height = this.targetEl.height()
    const self = this
    const defaultOpacity = 0
    const nodeSize = 10
    const textOffset = 3
    const linkSize = 10

    const arcPath = (leftHand, d) => {
      let start = leftHand ? d.source : d.target
      let end = leftHand ? d.target : d.source
      let dx = end.x - start.x
      let dy = end.y - start.y
      let dr = d.totalCount === 1
              ? 0 : Math.sqrt(Math.pow(dx, 2) + Math.pow(dy, 2)) / (1 + (1 / d.totalCount) * (d.count - 1))
      let sweep = leftHand ? 0 : 1
      return `M${start.x},${start.y}A${dr},${dr} 0 0,${sweep} ${end.x},${end.y}`
    }
    // Use elliptical arc path segments to doubly-encode directionality.
    const tick = () => {
      // Links
      linkPath.attr('d', function(d) {
        return arcPath(true, d)
      })
      textPath.attr('d', function(d) {
        return arcPath(d.source.x < d.target.x, d)
      })
      // Nodes
      circle.attr('transform', (d) => `translate(${d.x},${d.y})`)
      text.attr('transform', (d) => `translate(${d.x},${d.y})`)
    }

    const setOpacity = (scale) => {
      let opacity = scale >= +transformationConfig.d3Graph.zoom.minScale ? 1 : 0
      this.svg.selectAll('.nodeLabel')
        .style('opacity', opacity)
      this.svg.selectAll('textPath')
        .style('opacity', opacity)
    }

    const zoom = d3.behavior.zoom()
      .scaleExtent([1, 10])
      .on('zoom', () => {
        console.log('zoom')
        setOpacity(d3.event.scale)
        container.attr('transform', `translate(${d3.event.translate})scale(${d3.event.scale})`)
      })

    this.svg = d3.select(`#${this.containerId} svg`)
      .attr('width', width)
      .attr('height', height)
      .call(zoom)

    this.force = d3.layout.force()
      .charge(transformationConfig.d3Graph.forceLayout.charge)
      .linkDistance(transformationConfig.d3Graph.forceLayout.linkDistance)
      .on('tick', tick)
      .nodes(networkData.graph.nodes)
      .links(networkData.graph.edges)
      .size([width, height])
      .on('start', () => {
        console.log('force layout start')
        this.$timeout(() => { this.force.stop() }, transformationConfig.d3Graph.forceLayout.timeout)
      })
      .on('end', () => {
        console.log('force layout stop')
        setOpacity(zoom.scale())
      })
      .start()

    const renderFooterOnClick = (entity, type) => {
      const footerId = this.containerId + '_footer'
      const obj = {id: entity.id, label: entity.defaultLabel || entity.label, type: type}
      let html = [`<li><b>${obj.type}_id:</b>&nbsp${obj.id}</li>`]
      if (obj.label) {
        html.push(`<li><b>${obj.type}_type:</b>&nbsp${obj.label}</li>`)
      }
      html = html.concat(_.map(entity.data, (v, k) => {
        return `<li><b>${k}:</b>&nbsp${v}</li>`
      }))
      angular.element('#' + footerId)
        .find('.list-inline')
        .empty()
        .append(html.join(''))
    }

    const drag = d3.behavior.drag()
      .origin((d) => d)
      .on('dragstart', function(d) {
        console.log('dragstart')
        d3.event.sourceEvent.stopPropagation()
        d3.select(this).classed('dragging', true)
        self.force.stop()
      })
      .on('drag', function(d) {
        console.log('drag')
        d.px += d3.event.dx
        d.py += d3.event.dy
        d.x += d3.event.dx
        d.y += d3.event.dy
      })
      .on('dragend', function(d) {
        console.log('dragend')
        d.fixed = true
        d3.select(this).classed('dragging', false)
        self.force.resume()
      })

    const container = this.svg.append('g')
    if (networkData.graph.directed) {
      container.append('svg:defs').selectAll('marker')
        .data(['arrowMarker-' + this.containerId])
        .enter()
        .append('svg:marker')
        .attr('id', String)
        .attr('viewBox', '0 -5 10 10')
        .attr('refX', 16)
        .attr('refY', 0)
        .attr('markerWidth', 4)
        .attr('markerHeight', 4)
        .attr('orient', 'auto')
        .append('svg:path')
        .attr('d', 'M0,-5L10,0L0,5')
    }
    // Links
    const link = container.append('svg:g')
      .on('click', () => {
        renderFooterOnClick(d3.select(d3.event.target).datum(), 'edge')
      })
      .selectAll('g.link')
      .data(self.force.links())
      .enter()
      .append('g')
    const getPathId = (d) => this.containerId + '_' + d.source.index + '_' + d.target.index + '_' + d.count
    const showLabel = (d) => this._showNodeLabel(d)
    const linkPath = link.append('svg:path')
      .attr('class', 'link')
      .attr('size', linkSize)
      .attr('marker-end', `url(#arrowMarker-${this.containerId})`)
    const textPath = link.append('svg:path')
      .attr('id', getPathId)
      .attr('class', 'textpath')
    container.append('svg:g')
      .selectAll('.pathLabel')
      .data(self.force.links())
      .enter()
      .append('svg:text')
      .attr('class', 'pathLabel')
      .append('svg:textPath')
      .attr('startOffset', '50%')
      .attr('text-anchor', 'middle')
      .attr('xlink:href', (d) => '#' + getPathId(d))
      .text((d) => d.label)
      .style('opacity', defaultOpacity)
    // Nodes
    const circle = container.append('svg:g')
      .on('click', () => {
        renderFooterOnClick(d3.select(d3.event.target).datum(), 'node')
      })
      .selectAll('circle')
      .data(self.force.nodes())
      .enter().append('svg:circle')
      .attr('r', (d) => nodeSize)
      .attr('fill', (d) => networkData.graph.labels && d.label in networkData.graph.labels
                  ? networkData.graph.labels[d.label] : '#000000')
      .call(drag)
    const text = container.append('svg:g').selectAll('g')
      .data(self.force.nodes())
      .enter().append('svg:g')
    text.append('svg:text')
        .attr('x', (d) => nodeSize + textOffset)
        .attr('size', nodeSize)
        .attr('y', '.31em')
        .attr('class', (d) => 'nodeLabel shadow label-' + d.label)
        .text(showLabel)
        .style('opacity', defaultOpacity)
    text.append('svg:text')
        .attr('x', (d) => nodeSize + textOffset)
        .attr('size', nodeSize)
        .attr('y', '.31em')
        .attr('class', (d) => 'nodeLabel label-' + d.label)
        .text(showLabel)
        .style('opacity', defaultOpacity)
  }

  destroy() {
  }

  _showNodeLabel(d) {
    const transformationConfig = this.transformation.getSetting().scope.config
    const selectedLabel = (transformationConfig.properties[d.label] || {selected: 'label'}).selected
    return d.data[selectedLabel] || d[selectedLabel]
  }

  getTransformation() {
    return this.transformation
  }

  setNodesDefaults() {
  }

  _setEdgesDefaults(graph) {
    graph.edges
      .sort((a, b) => {
        if (a.source > b.source) {
          return 1
        } else if (a.source < b.source) {
          return -1
        } else if (a.target > b.target) {
          return 1
        } else if (a.target < b.target) {
          return -1
        } else {
          return 0
        }
      })
    graph.edges
      .forEach((edge, index) => {
        let prevEdge = graph.edges[index - 1]
        edge.count = (index > 0 && +edge.source === +prevEdge.source && +edge.target === +prevEdge.target
            ? prevEdge.count : 0) + 1
        edge.totalCount = graph.edges
          .filter((innerEdge) => +edge.source === +innerEdge.source && +edge.target === +innerEdge.target)
          .length
      })
    graph.edges
      .forEach((edge) => {
        if (typeof +edge.source === 'number') {
          // edge.source = graph.nodes.filter((node) => +edge.source === +node.id)[0] || null
          edge.source = _.find(graph.nodes, (node) => +edge.source === +node.id)
        }
        if (typeof +edge.target === 'number') {
          // edge.target = graph.nodes.filter((node) => +edge.target === +node.id)[0] || null
          edge.target = _.find(graph.nodes, (node) => +edge.target === +node.id)
        }
      })
  }

  getNetworkProperties(graph) {
    const baseCols = ['id', 'label']
    const properties = {}
    graph.nodes.forEach(function(node) {
      const hasLabel = 'label' in node && node.label !== ''
      if (!hasLabel) {
        return
      }
      const label = node.label
      const hasKey = hasLabel && label in properties
      const keys = _.uniq(Object.keys(node.data || {})
              .concat(hasKey ? properties[label].keys : baseCols))
      if (!hasKey) {
        properties[label] = {selected: 'label'}
      }
      properties[label].keys = keys
    })
    return properties
  }
}
