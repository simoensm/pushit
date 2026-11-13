package com.pushitup.feature.leaderboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.pushitup.domain.model.UserProfile
import com.pushitup.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LeaderboardTab { GLOBAL, FRIENDS }

data class LeaderboardEntry(
    val profile      : UserProfile,
    val rank         : Int,
    val isCurrentUser: Boolean,
)

data class LeaderboardUiState(
    val tab                    : LeaderboardTab        = LeaderboardTab.GLOBAL,
    val globalEntries          : List<LeaderboardEntry> = emptyList(),
    val friendsEntries         : List<LeaderboardEntry> = emptyList(),
    val isLoadingGlobal        : Boolean               = true,
    val isLoadingFriends       : Boolean               = true,
    val currentUserProfile     : UserProfile?          = null,
    val error                  : String?               = null,
    val viewingProfileTodayReps: Int                   = 0,
    val actionError            : String?               = null,
    val actionSuccess          : String?               = null,
)

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val userRepo: UserRepository,
    private val auth    : FirebaseAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState())
    val uiState: StateFlow<LeaderboardUiState> = _uiState.asStateFlow()

    init { load() }

    fun setTab(tab: LeaderboardTab) = _uiState.update { it.copy(tab = tab) }

    fun loadProfileExtra(uid: String) {
        viewModelScope.launch {
            val reps = userRepo.getTodayReps(uid)
            _uiState.update { it.copy(viewingProfileTodayReps = reps) }
        }
    }

    fun sendFriendRequest(toUid: String) {
        val myUid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            val result = userRepo.sendFriendRequest(myUid, toUid)
            if (result.isSuccess) {
                _uiState.update { it.copy(actionSuccess = "Friend request sent!") }
                // Refresh so the friends list includes this new pending request
                load()
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Could not send request"
                _uiState.update { it.copy(actionError = msg) }
            }
        }
    }

    fun isFriend(uid: String): Boolean =
        _uiState.value.currentUserProfile?.friends?.contains(uid) ?: false

    fun clearMessages() = _uiState.update { it.copy(actionError = null, actionSuccess = null) }

    fun refresh() {
        _uiState.update { it.copy(isLoadingGlobal = true, isLoadingFriends = true, error = null) }
        load()
    }

    private fun load() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            // Load current user profile first — needed for friends list
            val myProfileResult = userRepo.getProfile(uid)
            val myProfile = myProfileResult.getOrNull()
            _uiState.update { it.copy(currentUserProfile = myProfile) }

            // Load global and friends in parallel
            val globalDeferred  = async { loadGlobal(uid) }
            val friendsDeferred = async { loadFriends(uid, myProfile) }
            globalDeferred.await()
            friendsDeferred.await()
        }
    }

    private suspend fun loadGlobal(myUid: String) {
        val result = userRepo.getTopUsers(100)
        result.fold(
            onSuccess = { users ->
                val entries = users.mapIndexed { idx, profile ->
                    LeaderboardEntry(
                        profile       = profile,
                        rank          = idx + 1,
                        isCurrentUser = profile.uid == myUid,
                    )
                }
                _uiState.update { it.copy(globalEntries = entries, isLoadingGlobal = false) }
            },
            onFailure = { e ->
                _uiState.update { it.copy(isLoadingGlobal = false, error = e.localizedMessage) }
            },
        )
    }

    private suspend fun loadFriends(myUid: String, myProfile: UserProfile?) {
        val friendUids = myProfile?.friends ?: emptyList()
        // Fetch friend profiles + inject self
        val friendProfiles = if (friendUids.isEmpty()) {
            emptyList()
        } else {
            userRepo.getProfilesByUids(friendUids).getOrDefault(emptyList())
        }
        // Merge self into the list (self may not be in friendUids)
        val allProfiles = (friendProfiles + listOfNotNull(myProfile))
            .distinctBy { it.uid }
            .sortedByDescending { it.stars }

        val entries = allProfiles.mapIndexed { idx, profile ->
            LeaderboardEntry(
                profile       = profile,
                rank          = idx + 1,
                isCurrentUser = profile.uid == myUid,
            )
        }
        _uiState.update { it.copy(friendsEntries = entries, isLoadingFriends = false) }
    }
}
