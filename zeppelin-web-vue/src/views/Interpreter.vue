<template>
  <div class="interpreters">
    <InterpreterList />
  </div>
</template>

<script>
import InterpreterList from '@/components/Interpreter/InterpreterList.vue'

export default {
  name: 'interpreter',
  components: {
    InterpreterList
  }
}
</script>
