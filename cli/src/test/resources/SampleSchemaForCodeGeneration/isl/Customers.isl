$ion_schema_2_0

type::{
  name: Customer,
  type: struct,
  fields: closed::{
    firstName: {
      type: string,
      regex: "[-A-Za-z ]+",
      occurs: required
    },
    middleName: {
      type: string,
      regex: "[-A-Za-z ]+",
    },
    lastName: {
      type: string,
      occurs: required,
      regex: "[-A-Za-z ]+",
    },
  },
}

type::{
  name: SeniorCustomer,
  type: struct,
  fields: closed::{
    firstName: { type: string, occurs: required },
    middleName: string,
    lastName: { type: string, occurs: required },
    age: { type: int, occurs: required },
  },
}

type::{
  name: SpecialCustomer,
  type: struct,
  fields: closed::{
    firstName: { type: string, occurs: required },
    middleName: string,
    lastName: { type: string, occurs: required },
    // specialData: $any,
  },
}
