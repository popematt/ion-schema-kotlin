package com.amazon.ionschema.stream

import com.amazon.ionschema.streaming.IonReaderValue

class StreamState {
    fun handleNext(value: IonReaderValue) {

    }
    fun handleStepIn(value: IonReaderValue): StreamState {

    }
    fun handleStepOut() {

    }
}

class StreamType {
    fun handleNext(value: IonReaderValue) {

    }
    fun handleStepIn(value: IonReaderValue): StreamType {

    }
    fun handleStepOut() {

    }
}

interface StreamConstraint


class ElementStreamConstraint(private val type: StreamType) {
    fun handleNext(value: IonReaderValue) {

    }
    fun handleStepIn(value: IonReaderValue): StreamType {

    }
    fun handleStepOut() {

    }
}