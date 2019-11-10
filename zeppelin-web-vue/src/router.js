import Vue from 'vue'
import VueRouter from 'vue-router'

import TabManagerView from './views/TabManagerView.vue'

window.popStateDetected = false
window.addEventListener('popstate', () => {
  window.popStateDetected = true
})

Vue.use(VueRouter)

export default new VueRouter({
  routes: [
    {
      path: '/',
      name: 'TabManagerView',
      component: TabManagerView
    }, {
      path: '/:tabId',
      name: 'TabManagerNoteView',
      component: TabManagerView
    }
  ]
})
