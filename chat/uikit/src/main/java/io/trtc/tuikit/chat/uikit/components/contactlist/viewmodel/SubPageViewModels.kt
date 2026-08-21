package io.trtc.tuikit.chat.uikit.components.contactlist.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.contact.FriendApplicationInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupApplicationInfo
import io.trtc.tuikit.atomicxcore.api.group.GroupStore
import io.trtc.tuikit.chat.uikit.components.config.BusinessAction
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionCompletion
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionRegistry
import io.trtc.tuikit.chat.uikit.components.config.BusinessActionResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class BlacklistSubViewModel(
    private val contactStore: ContactStore
) : ViewModel() {

    val blacklistUsers: StateFlow<List<ContactInfo>> = contactStore.state.blackList.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        contactStore.loadBlackList(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
    }
}

class FriendApplicationSubViewModel(
    private val contactStore: ContactStore
) : ViewModel() {

    val friendApplications: StateFlow<List<FriendApplicationInfo>>
        get() = contactStore.state.friendApplicationList

    init {
        contactStore.loadFriendApplications(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })

        contactStore.clearFriendApplicationUnreadCount(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
    }

    fun acceptFriendApplication(
        application: FriendApplicationInfo,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        if (BusinessActionRegistry.dispatch(
                BusinessAction.HandleFriendApplication(application.userID, true),
                businessCompletion({ contactStore.loadFriendApplications(); contactStore.loadFriends(); onSuccess() }, onFailure)
            )
        ) return
        contactStore.acceptFriendApplication(application, object : CompletionHandler {
            override fun onSuccess() {
                onSuccess()
            }

            override fun onFailure(code: Int, desc: String) {
                onFailure(desc)
            }
        })
    }

    fun refuseFriendApplication(
        application: FriendApplicationInfo,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        if (BusinessActionRegistry.dispatch(
                BusinessAction.HandleFriendApplication(application.userID, false),
                businessCompletion({ contactStore.loadFriendApplications(); onSuccess() }, onFailure)
            )
        ) return
        contactStore.refuseFriendApplication(application, object : CompletionHandler {
            override fun onSuccess() {
                onSuccess()
            }

            override fun onFailure(code: Int, desc: String) {
                onFailure(desc)
            }
        })
    }

    private fun businessCompletion(
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ): BusinessActionCompletion = object : BusinessActionCompletion {
        override fun onSuccess(result: BusinessActionResult) = onSuccess()
        override fun onFailure(code: Int, description: String) = onFailure(description)
    }
}

class GroupApplicationSubViewModel(
    private val groupStore: GroupStore
) : ViewModel() {

    val groupApplications: StateFlow<List<GroupApplicationInfo>>
        get() = groupStore.state.applicationList

    init {
        groupStore.loadApplications(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })

        groupStore.clearApplicationUnreadCount(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
    }

    fun acceptApplication(
        application: GroupApplicationInfo,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        if (BusinessActionRegistry.dispatch(
                BusinessAction.HandleGroupApplication(
                    application.applicationID,
                    application.groupID,
                    application.fromUser.orEmpty(),
                    true
                ),
                businessCompletion({ groupStore.loadApplications(); groupStore.loadJoinedGroups(); onSuccess() }, onFailure)
            )
        ) return
        groupStore.acceptApplication(application, object : CompletionHandler {
            override fun onSuccess() {
                onSuccess()
            }

            override fun onFailure(code: Int, desc: String) {
                onFailure(desc)
            }
        })
    }

    fun refuseApplication(
        application: GroupApplicationInfo,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        if (BusinessActionRegistry.dispatch(
                BusinessAction.HandleGroupApplication(
                    application.applicationID,
                    application.groupID,
                    application.fromUser.orEmpty(),
                    false
                ),
                businessCompletion({ groupStore.loadApplications(); onSuccess() }, onFailure)
            )
        ) return
        groupStore.refuseApplication(application, object : CompletionHandler {
            override fun onSuccess() {
                onSuccess()
            }

            override fun onFailure(code: Int, desc: String) {
                onFailure(desc)
            }
        })
    }

    private fun businessCompletion(
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ): BusinessActionCompletion = object : BusinessActionCompletion {
        override fun onSuccess(result: BusinessActionResult) = onSuccess()
        override fun onFailure(code: Int, description: String) = onFailure(description)
    }
}

class MyGroupSubViewModel(
    private val groupStore: GroupStore
) : ViewModel() {

    val groups: StateFlow<List<ContactInfo>> = groupStore.state.joinedGroupList
        .map { list ->
            list.map { info ->
                ContactInfo(
                    userID = info.groupID,
                    avatarURL = info.avatarURL,
                    nickname = info.groupName
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        groupStore.loadJoinedGroups(object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) {}
        })
    }
}

class BlacklistSubViewModelFactory(
    private val contactStore: ContactStore = ContactStore.shared
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BlacklistSubViewModel::class.java)) {
            return BlacklistSubViewModel(contactStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

class FriendApplicationSubViewModelFactory(
    private val contactStore: ContactStore = ContactStore.shared
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FriendApplicationSubViewModel::class.java)) {
            return FriendApplicationSubViewModel(contactStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

class GroupApplicationSubViewModelFactory(
    private val groupStore: GroupStore = GroupStore.shared
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GroupApplicationSubViewModel::class.java)) {
            return GroupApplicationSubViewModel(groupStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

class MyGroupSubViewModelFactory(
    private val groupStore: GroupStore = GroupStore.shared
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MyGroupSubViewModel::class.java)) {
            return MyGroupSubViewModel(groupStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
