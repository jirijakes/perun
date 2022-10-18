import perun.proto.bolt.doc.*

// val docstr = "hello ~field~ dear"
// parser.parseAll(docstr)

val str = """
if ~signature~ is not a valid signature, using ~node_id~ of the double-SHA256 of the entire message following the ~signature~ field (including unknown fields following ~fee_proportional_millionths~):

    - SHOULD send a ~warning~ and close the connection.
    - MUST NOT process the message further.
"""

val prd = new Prd("if ~signature~ SHOULD not")
prd.haha.run()
