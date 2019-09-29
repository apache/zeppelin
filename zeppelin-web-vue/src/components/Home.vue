<template>
  <div class="home">
    <h1>{{ msg }}</h1>
    <p>
      For a guide and recipes on how to configure / customize this project,<br>
      check out the
      <a
        href="https://zeppelin.apache.org/docs/latest/index.html"
        target="_blank"
        rel="noopener"
      >
        zeppelin documentation
      </a>
    </p>
  </div>
</template>

<script>
export default {
  name: 'Home',
  props: {
    msg: String
  }
}
</script>

<style lang="scss" scoped>
.home {
  padding-top: 100px;
  text-align: center;
}
a {
  color: #2f71a9;
}
</style>
