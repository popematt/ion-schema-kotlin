$ion_schema_2_0

type::{
  name: tree_node,
  fields: closed::{
    value: { occurs: required, type: int },
    left: tree_node,
    right: tree_node
  }
}