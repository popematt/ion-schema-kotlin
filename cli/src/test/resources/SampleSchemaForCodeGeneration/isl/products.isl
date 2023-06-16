$ion_schema_2_0

schema_header::{
  imports:[
    { id: "employees.isl" }
  ]
}

//type::{
//  name: sandwich,
//  fields: closed::{
//    condiments: { type: list, element: condiment },
//  }
//}

type::{
  $doc: '''
  Things you can put in a sandwich, hamburger, etc.
  Condiment is specifically defined as a _spreadable_ thing in this case, and does not include anything that is (subjectively) too unconventional.
  ''',
  name: condiment,
  valid_values: [
    butter,
    cream_cheese,
    ketchup,
    mayonnaise,
    mustard,
    peanut_butter,
    strawberry_jam,
    raspberry_jam,
    relish,
  ]
}

type::{
  name: sandwich,
  fields: closed::{
    bread: bool
  }
}


type::{
  name: taco,
  fields: closed::{
    shell: { occurs:required, valid_values: [hard, soft] }
  }
}


type::{
  name: salad,
  fields: closed::{
    name: { type: string, codepoint_length: range::[1, 32] },
    vegetarian: bool
  }
}

type::{
  name: soup,
  fields: closed::{
    broth: { fields: closed::{ temp: int, flavor: { valid_values: [chicken, vegetable] } } }
  }
}

type::{
  name: int_map,
  type: struct,
  element: int,
  $codegen_use: {
    kotlin: {
      type: "kotlin.Map<kotlin.Int, kotlin.Int>",
      serde: "com.amazon.kitchen.IntToIntMapSerde",
    }
  }
}

type::{
  name: alphabet_soup,
  $doc: '''
  A special type of soup that has letters (or other graphemes) in it.
  ''',
  fields: closed::{
    name: { type: string, occurs: required, codepoint_length: range::[min, 32] },
    description: string,
    broth: {
      occurs: required,
      fields: closed::{
        temp: int,
        flavor: { valid_values: [chicken, vegetable] }
      }
    },
    letters: { occurs: required, type: list, element: string },
    condiment: condiment,
    eater: { id: "Customers.isl", type: Customer },
    cook: cook,
  }
}

type::{
  name: waffle,
  fields: closed::{
    topping_grid: {
      occurs: required,
      type: list,
      element: {
        type: list,
        element: string,
      }
    }
  }
}


type::{
  name: menu_item,
  one_of: [
    sandwich,
    taco,
    salad,
    soup,
  ]
}


