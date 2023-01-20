/*
 * Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.ionschema.internal.constraint

/**
 * An approximation of a Non-deterministic Finite-State Automaton for evaluating regular languages (and analogues thereof).
 *
 * This implementation uses the following algorithm.
 * > the NFA consumes a string of input symbols, one by one. In each step, whenever two or more transitions are applicable,
 * > it "clones" itself into appropriately many copies, each one following a different transition. If no transition is
 * > applicable, the current copy is in a dead end, and it "dies". If, after consuming the complete input, any of the
 * > copies is in an accept state, the input is accepted, else, it is rejected.
 *
 * See [Nondeterministic finite automaton](https://en.wikipedia.org/wiki/Nondeterministic_finite_automaton).
 *
 * This implementation is designed to track which state(s) are visited as well as how many consecutive visits have been
 * made to any given state. This specialization is intended to make it easier to support cases where an event could occur
 * a variable number of times (eg. regex `a{2,4}`).
 *
 * This implementation does not check for matches that are less than the full stream of events.
 *
 * For an example of how to model things using this class, see [OrderedElementsNfaStatesBuilder].
 */
internal class NFA<Event : Any, V: Any>(
    /**
     * An adjacency list describing the transitions between states in the NFA. The adjacency list is modeled as a map of
     * source to list of destinations. Any intermediate state that appears as a destination must also appear as a key in
     * the map. The map must contain [NFA.State.Initial] as a key. There may be no transitions out of [NFA.State.Final].
     */
    private val transitions: Map<State<Event, V>, Set<State<Event, V>>>,
) {

    init {
        require(!transitions[State.initial()].isNullOrEmpty()) { "Invalid graph: no transition from initial state." }
        require(transitions[State.final()].isNullOrEmpty()) { "Invalid graph: no transitions allowed from final state." }
        transitions.values.flatten().let { destinations ->
            val unknownDestinations = destinations.filter { it !in transitions.keys && it != State.Final }.distinct()
            require(unknownDestinations.isEmpty()) { "Invalid graph: transitions to unknown states: $unknownDestinations" }
            require(State.final() in destinations) { "Invalid graph: no transition to final state." }
        }
    }

    companion object { private val END_OF_STREAM_EVENT: Nothing? = null }
    private val INITIAL_STATE: TransitionsResult<in Event> = TransitionsResult.StateSet(setOf(StateVisitCount(State.initial(), 1)))

    sealed class State<Event, out V: Any> (val debugId: String) {
        open fun canEnter(event: Event?): Decision<V> = Decision(false)
        open fun canReenter(visits: Int): Boolean = false
        open fun canExit(visits: Int): Boolean = false
        final override fun toString(): String = debugId
        open val description: String = debugId

        data class Decision<out T>(val value: Boolean, val details: T? = null)

        companion object {
            fun <Event: Any> initial(): State<Event, Nothing> = Initial as State<Event, Nothing>
            fun <Event : Any> final(): State<Event, Nothing> = Final as State<Event, Nothing>
        }
        object Initial: State<Any, Nothing>("I") {
            override fun canExit(visits: Int): Boolean = true
            override fun canEnter(event: Any?): Decision<Nothing> = Decision(false)
            //override fun equals(other: Any?): Boolean = other is Final<*>
            //override fun hashCode(): Int = -1
        }
        object Final : State<Any, Nothing>("F") {
            override fun canEnter(event: Any?): Decision<Nothing> = Decision(event == END_OF_STREAM_EVENT)
            //override fun equals(other: Any?): Boolean = other is Final<*>
            //override fun hashCode(): Int = Int.MAX_VALUE
        }

        data class Intermediate<Event : Any, V: Any>(
            /** Unique identifier for this state. */
            val id: Int,
            /** Condition on the [Event] value for entering this state. */
            private val entryCondition: (Event) -> Decision<V>,
            /** Condition on number of visits for re-entering this state. Only applies on a loop (i.e. S1->S1, but not S1->S2->S1). */
            private val reentryCondition: (Int) -> Boolean,
            /** Condition on number of visits for leaving this state. Does not apply when following a loop (ie. S1->S1). */
            private val exitCondition: (Int) -> Boolean,

            override val description: String = "S$id",
        ) : State<Event, V>("S$id") {

            override fun canEnter(event: Event?): Decision<V> = event?.let(entryCondition) ?: Decision(false)
            override fun canReenter(visits: Int): Boolean = reentryCondition(visits)
            override fun canExit(visits: Int): Boolean = exitCondition(visits)

            override fun hashCode(): Int = id.hashCode()
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                return other is Intermediate<*, *> && id == other.id
            }
        }
    }

    /** Models the number of times a given state has been visited for a possible path of execution */
    private data class StateVisitCount<Event : Any, V: Any>(val state: State<Event, V>, val visits: Int) {
        override fun toString(): String = "${state.debugId}:$visits"
    }

    /** Models reasons why a transition cannot be made */
    internal sealed class InvalidTransition<Event: Any> {
        abstract val eventId: Int
        data class CannotEnterState<Event : Any, V: Any>(override val eventId: Int, val toState: State<Event, V>, val reason: V? = null) : InvalidTransition<Event>()
        data class CannotExitState<Event : Any, V: Any>(override val eventId: Int, val fromState: State<Event, V>) : InvalidTransition<Event>()
        data class CannotReenterState<Event : Any, V: Any>(override val eventId: Int, val state: State<Event, V>) : InvalidTransition<Event>()
    }

    /** Models the result of one pass of transitions */
    private sealed class TransitionsResult<Event: Any> {
        class StateSet<Event : Any, V: Any>(val states: Set<StateVisitCount<Event, V>>): TransitionsResult<Event>()
        class ErrSet<Event: Any>(val errs: Set<InvalidTransition<Event>>): TransitionsResult<Event>()
    }

    private fun TransitionsResult<in Event>.transition(idx: Int, event: Event?, debug: Boolean = false): TransitionsResult<in Event> {
        if (this !is TransitionsResult.StateSet<*, *>) return this

        val newStates = mutableSetOf<StateVisitCount<Event, V>>()
        val invalidTransitions = mutableSetOf<InvalidTransition<Event>>()

        // Cache the results of testing the entry conditions for states so that we aren't duplicating any work
        // when there are multiple visit counts for the same state.
        val entryConditionCache = mutableMapOf<State<Event, V>, State.Decision<V>>()

        this@transition.states.forEach { (transitionFromState, visits) ->
            transitionFromState as State<Event, V>
            val canExit = transitionFromState.canExit(visits)

            transitions[transitionFromState]?.forEach { transitionToState ->
                val (canEnter, details) = entryConditionCache.getOrPut(transitionToState) { transitionToState.canEnter(event) }
                val isLoop = transitionFromState == transitionToState
                val canVisitAgain = transitionToState.canReenter(visits + 1)

                if (isLoop) when {
                    !canEnter && canVisitAgain -> invalidTransitions.add(InvalidTransition.CannotEnterState(idx, transitionToState, details))
                    !canVisitAgain -> invalidTransitions.add(InvalidTransition.CannotReenterState(idx, transitionToState))
                    else -> newStates.add(StateVisitCount(transitionToState, visits + 1))
                } else when {
                    !canExit -> invalidTransitions.add(InvalidTransition.CannotExitState(idx, transitionFromState))
                    !canEnter -> invalidTransitions.add(InvalidTransition.CannotEnterState(idx, transitionToState, details))
                    else -> newStates.add(StateVisitCount(transitionToState, 1))
                }
            }
        }

        return if (newStates.isNotEmpty()) {
            if (debug) println("${idx.toString().padStart(4)} : $newStates")
            TransitionsResult.StateSet(newStates)
        } else {
            TransitionsResult.ErrSet(invalidTransitions)
        }
    }

    /**
     * Tests if a stream of [Event] is recognized by this state machine.
     */
    fun matches(events: Iterable<Event>, debug: Boolean = false): Outcome<Event> {
        if (debug) {
            println()
            println(transitions.entries.joinToString("\n") { (k, v) -> "${"$k".padEnd(3)} -> $v" })
            println("Transitions for input: $events")
            println("     : $INITIAL_STATE")
        }
        val result = events.foldIndexed(INITIAL_STATE) { idx, states, event -> states.transition(idx, event, debug) }
            .transition(events.count(), END_OF_STREAM_EVENT, debug)

        return when (result) {
            is TransitionsResult.StateSet<*, *> -> Outcome.IsMatch
            is TransitionsResult.ErrSet -> Outcome.IsNotMatch(result.errs)
        }
    }

    sealed class Outcome<out Event: Any> {
        object IsMatch: Outcome<Nothing>()
        data class IsNotMatch<Event: Any>(val reasons: Set<InvalidTransition<in Event>>): Outcome<Event>()
    }
}
