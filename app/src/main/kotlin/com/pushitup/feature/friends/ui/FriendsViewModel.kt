package com.pushitup.feature.friends.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pushitup.domain.model.FriendRequest
import com.pushitup.domain.model.UserProfile
import com.pushitup.domain.repository.AuthRepository
import com.pushitup.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendsUiState(
    val friends               : List<UserProfile>  = emptyList(),
    val incomingRequests      : List<FriendRequest> = emptyList(),
    /** UIDs of users we've already sent a pending request to (for "Pending" chip in search). */
    val pendingToUids         : Set<String>         = emptySet(),
    val searchResults         : List<UserProfile>  = emptyList(),
    val isSearching           : Boolean            = false,
    val searchQuery           : String             = "",
    val actionError           : String?            = null,
    val actionSuccess         : String?            = null,
    /** Today's reps for the profile currently open in the dialog. */
    val viewingProfileTodayReps: Int               = 0,
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val userRepo: UserRepository,
) : ViewModel() {

    val uiState: StateFlow<FriendsUiState> get() = _uiState
    private val _uiState = MutableStateFlow(FriendsUiState())

    private val _searchQuery = MutableStateFlow("")

    val myUid: String get() = authRepo.currentUid ?: ""

    init {
        setup()
    }

    @OptIn(FlowPreview::class)
    private fun setup() {
        val uid = authRepo.currentUid ?: return

        userRepo.friendsFlow(uid)
            .onEach { friends -> _uiState.update { it.copy(friends = friends) } }
            .launchIn(viewModelScope)

        userRepo.incomingRequestsFlow(uid)
            .onEach { reqs -> _uiState.update { it.copy(incomingRequests = reqs) } }
            .launchIn(viewModelScope)

        userRepo.outgoingRequestsFlow(uid)
            .onEach { reqs -> _uiState.update { it.copy(pendingToUids = reqs.map { it.toUid }.toSet()) } }
            .launchIn(viewModelScope)

        _searchQuery
            .debounce(400)
            .distinctUntilChanged()
            .onEach { query ->
                if (query.length < 2) {
                    _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
                    return@onEach
                }
                _uiState.update { it.copy(isSearching = true) }
                val result = userRepo.searchByUsername(query)
                _uiState.update { state ->
                    state.copy(
                        isSearching   = false,
                        searchResults = result.getOrDefault(emptyList()).filter { it.uid != uid },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChanged(q: String) {
        _searchQuery.value = q
        _uiState.update { it.copy(searchQuery = q) }
    }

    fun sendRequest(toUid: String) {
        val uid = authRepo.currentUid ?: return
        // Guard locally — prevents double-tap UI glitch while Firestore enforces server-side
        if (toUid in _uiState.value.pendingToUids || _uiState.value.friends.any { it.uid == toUid }) return
        viewModelScope.launch {
            val result = userRepo.sendFriendRequest(uid, toUid)
            if (result.isSuccess) {
                _uiState.update { it.copy(actionSuccess = "Friend request sent!") }
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Could not send request"
                _uiState.update { it.copy(actionError = msg) }
            }
        }
    }

    fun acceptRequest(request: FriendRequest) {
        val uid = authRepo.currentUid ?: return
        viewModelScope.launch {
            val result = userRepo.acceptFriendRequest(request.id, uid, request.fromUid)
            if (result.isSuccess) {
                _uiState.update { it.copy(actionSuccess = "Now friends with ${request.fromDisplayName}!") }
            } else {
                _uiState.update { it.copy(actionError = result.exceptionOrNull()?.localizedMessage ?: "Failed to accept request") }
            }
        }
    }

    fun declineRequest(request: FriendRequest) {
        viewModelScope.launch {
            userRepo.declineFriendRequest(request.id)
        }
    }

    fun removeFriend(otherUid: String) {
        val uid = authRepo.currentUid ?: return
        viewModelScope.launch { userRepo.removeFriend(uid, otherUid) }
    }

    fun loadProfileExtra(uid: String) {
        viewModelScope.launch {
            val reps = userRepo.getTodayReps(uid)
            _uiState.update { it.copy(viewingProfileTodayReps = reps) }
        }
    }

    fun clearMessages() = _uiState.update { it.copy(actionError = null, actionSuccess = null) }
}
