export default {
  mergeArray (a, b, prop) {
    return b.map((itemb) => {
      let srcItem = a.find(itema => itema[prop] === itemb[prop])
      return srcItem? { ...srcItem, ...itemb } : itemb
    })
  }
}
