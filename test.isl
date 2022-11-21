$ion_schema_2_0

type::{
  name: foo,
  field_names: distinct::{ codepoint_length: 4 }
}

type::{
  name: log_stream,
  element: {
    type: struct,
    fields: {
      time: { occurs: required, type: timestamp },
      message: { occurs: required, type: string },
    }
  }
}


type::{
  name: tree,
  one_of: [
    treeAncestor,
    treeLeaf,
  ]
}

type::{
  name: treeAncestor,
  type: sexp,
  element: {
    type: $null_or::tree,
    annotations: any
  }
}

type::{
  name: treeLeaf,
  not: sexp
}

