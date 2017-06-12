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
    console.log('Render Graph Visualization')

    let transformationConfig = this.transformation.getSetting().scope.config
    console.log('cfg', transformationConfig)
    if (transformationConfig && angular.equals({}, transformationConfig.properties)) {
      transformationConfig.properties = networkData.getNetworkProperties()
    }

    this.targetEl.empty().append('<svg></svg>')

    let width = this.targetEl.width()
    let height = this.targetEl.height()
    let self = this
    let defaultOpacity = 0
    let nodeSize = 10
    let textOffset = 3
    let linkSize = 10

    let arcPath = (leftHand, d) => {
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
    let tick = () => {
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

    let setOpacity = (scale) => {
      let opacity = scale >= +transformationConfig.d3Graph.zoom.minScale ? 1 : 0
      this.svg.selectAll('.nodeLabel')
        .style('opacity', opacity)
      this.svg.selectAll('textPath')
        .style('opacity', opacity)
    }

    let zoom = d3.behavior.zoom()
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

    let renderFooterOnClick = (entity, type) => {
      let footerId = this.containerId + '_footer'
      let obj = {id: entity.id, label: entity.defaultLabel || entity.label, type: type}
      let html = [this.$interpolate(['<li><b>{{type}}_id:</b>&nbsp{{id}}</li>',
        '<li><b>{{type}}_type:</b>&nbsp{{label}}</li>'].join(''))(obj)]
      html = html.concat(_.map(entity.data, (v, k) => {
        return this.$interpolate('<li><b>{{field}}:</b>&nbsp{{value}}</li>')({field: k, value: v})
      }))
      angular.element('#' + footerId)
        .find('.list-inline')
        .empty()
        .append(html.join(''))
    }

    let drag = d3.behavior.drag()
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

    let container = this.svg.append('g')
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
    let link = container.append('svg:g')
      .on('click', () => {
        renderFooterOnClick(d3.select(d3.event.target).datum(), 'edge')
      })
      .selectAll('g.link')
      .data(self.force.links())
      .enter()
      .append('g')
    let getPathId = (d) => this.containerId + '_' + d.source.index + '_' + d.target.index + '_' + d.count
    let showLabel = (d) => this._showNodeLabel(d)
    let linkPath = link.append('svg:path')
      .attr('class', 'link')
      .attr('size', linkSize)
      .attr('marker-end', `url(#arrowMarker-${this.containerId})`)
    let textPath = link.append('svg:path')
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
    let circle = container.append('svg:g')
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
    let text = container.append('svg:g').selectAll('g')
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
    let transformationConfig = this.transformation.getSetting().scope.config
    let selectedLabel = (transformationConfig.properties[d.label] || {selected: 'label'}).selected
    return d.data[selectedLabel] || d[selectedLabel]
  }

  getTransformation() {
    return this.transformation
  }
}
