<template>
  <div class="interpreters">
    <div
      v-for="(interpreter, index) in this.$store.state.InterpreterStore.interpreters"
      v-bind:key="index"
    >
      {{ interpreter.id }}
    </div>
  </div>
</template>

<script>
import { mapState } from 'vuex'

export default {
  name: 'InterpreterList',
  computed: {
    ...mapState({
      interpreters: 'InterpreterStore/interpreters'
    })
  },
  created () {

  },
  mounted () {
    this.getInterpreters()
  },

  methods: {
    getInterpreters () {
      this.$store.dispatch('getInterpreters')
    }
  }
}
</script>

<style lang="scss" scoped>
.interpreters {
  list-style: none;
  margin: 0;
  padding: 0;

  ul {
    list-style: none;
    margin: 0;
    padding: 0;

    li {
      a {
        font-size: 14px;
        padding: 5px 10px;
        display: block;
        border-left: 4px solid transparent;

        &.active {
          background: #f1eeee;
          border-left-color: #2f71a9;
        }

        &:hover {
          background: #F1F1F1;

        }
      }
    }
  }
}
</style>
