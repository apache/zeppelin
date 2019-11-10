import Vue from 'vue'

import App from './App.vue'
import router from './router'
import store from './store'

import Antd from 'ant-design-vue'

import VueSplit from 'vue-split-panel'
import VueOffline from 'vue-offline'

import 'ant-design-vue/dist/antd.css'

import api from '@/services/api.js'
import { EventBus } from '@/services/event-bus'
import commandManager from '@/services/command-manager'
import { i18n } from '@/i18n'

window.$ = window.jQuery = require('jquery')

Vue.use(Antd)

Vue.config.productionTip = false

Vue.use(VueSplit)
Vue.use(VueOffline, {
  mixin: false
});

(() => {
  fetch(api.getRestApiBase() + '/security/ticket')
    .then((response) => {
      return response.json()
    })
    .then((response) => {
      new Vue({
        data: {
          ticket: response.body,

          TRASH_FOLDER_ID: '~Trash'
        },
        beforeMount () {
          commandManager.setupCommands(this.$store)
        },
        methods: {
          executeCommand (type, command, arg) {
            EventBus.$emit(type, command, arg)
          }
        },
        router,
        store,
        i18n,
        render: h => h(App),
        renderError (h, err) {
          return h('pre', { style: { color: 'red' } }, err.stack)
        }
      }).$mount('#app')
    })
})()
