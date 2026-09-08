package com.vexcompany.samvira.domain.org

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the currently selected organization as observable app state.
 *
 * Selection is process-scoped: it resets to [OrganizationSelection.None] on
 * app restart, which fails closed (no stale cross-organization context can
 * leak into a new process). Persisting the selection across launches can be
 * added later without changing consumers of this class.
 */
class OrganizationSelectionStore {

    private val _selection = MutableStateFlow<OrganizationSelection>(OrganizationSelection.None)
    val selection: StateFlow<OrganizationSelection> = _selection.asStateFlow()

    fun select(organization: Organization) {
        _selection.value = OrganizationSelection.Selected(organization)
    }

    fun clear() {
        _selection.value = OrganizationSelection.None
    }
}
