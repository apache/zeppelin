package org.apache.zeppelin

// TODO: Currently always create a new spark context, need to use the existing one
// TODO: Keeping interpreter out of spark interpreter group for now, until the context sharing code is developed
// TODO: Add rmr, rhive
// TODO: Link getProgress to plyr (and knitr progress) if possible
// TODO: Forms?
// TODO: Completion?  Currently commented-out
// TODO: ZeppelinContext get, put, show()?  Currently put() sort-of works, but is not linked to the actual ZeppelinContext because the interpreters are not part of the Spark group.
// TODO: It would be nice if the RReplInterpreter output svg instead of jpg, or intelligently selected, at a minimum
// TODO: Confirm that an update to the properties of one interpreter in the group updates all of them and the context and vice versa

/**
 * Created by aelberg on 8/5/15.
 */
package object rinterpreter {
}
