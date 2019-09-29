import Vue from 'vue'
import VueRouter from 'vue-router'

import TabManagerView from './views/TabManagerView.vue'

Vue.use(VueRouter)

export default new VueRouter({
  routes: [
    {
      path: '/',
      name: 'TabManagerView',
      component: TabManagerView
    }
  ]
})
