package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.local.entity.ActorEntity
import com.example.data.local.entity.LinkEntity
import com.example.data.local.entity.StudioEntity
import com.example.ui.MainViewModel
import com.example.ui.ScreenState
import com.example.ui.SortMode
import com.example.ui.components.ActorAvatarPlaceholder
import com.example.ui.components.LinkCard
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalBetaTestPrivacy
import com.example.ui.theme.LocalVaultPalette
import com.example.ui.theme.privacyImageBlur

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val links by viewModel.filteredLinks.collectAsStateWithLifecycle()
    val actors by viewModel.allActors.collectAsStateWithLifecycle()
    val studios by viewModel.allStudios.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currentSort by viewModel.sortMode.collectAsStateWithLifecycle()
    val bookmarkedIds by viewModel.bookmarkedIds.collectAsStateWithLifecycle()
    val viewFilter by viewModel.viewFilter.collectAsStateWithLifecycle()
    val resolvingStatus by viewModel.resolvingVideoStatus.collectAsStateWithLifecycle()
    val resolvingCardId by viewModel.resolvingCardId.collectAsStateWithLifecycle()
    val videoResolutionError by viewModel.videoResolutionError.collectAsStateWithLifecycle()
    val activeInlineVideo by viewModel.activeInlineVideo.collectAsStateWithLifecycle()
    val currentScreen by viewModel.screenState.collectAsStateWithLifecycle()

    val targetActor = remember(currentScreen, actors) {
        if (currentScreen is ScreenState.ActorScenes) {
            val id = (currentScreen as ScreenState.ActorScenes).actorId
            actors.firstOrNull { it.id == id }
        } else null
    }

    val targetStudio = remember(currentScreen, studios) {
        if (currentScreen is ScreenState.StudioScenes) {
            val id = (currentScreen as ScreenState.StudioScenes).studioId
            studios.firstOrNull { it.id == id }
        } else null
    }

    val displayedLinks = remember(links, currentScreen, targetActor, targetStudio) {
        when (currentScreen) {
            is ScreenState.ActorScenes -> {
                val actorId = (currentScreen as ScreenState.ActorScenes).actorId
                links.filter { it.actorIds.contains(actorId) || (targetActor != null && it.actorIds.contains(targetActor.name)) }
            }
            is ScreenState.StudioScenes -> {
                val studioId = (currentScreen as ScreenState.StudioScenes).studioId
                links.filter { it.studioIds.contains(studioId) || (targetStudio != null && it.studioIds.contains(targetStudio.name)) }
            }
            else -> links
        }
    }

    // O(1) Precomputed Fast Lookup Maps - computed once at Screen level on data change
    val actorsMap = remember(actors) {
        actors.associate { it.id to it.name }
    }
    val fullActorsMap = remember(actors) {
        actors.associateBy { it.id }
    }
    val studiosMap = remember(studios) {
        studios.associate { it.id to it.name }
    }

    var isSearchExpanded by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var activeOverlayCardId by remember { mutableStateOf<String?>(null) }
    var showEditActorDialog by remember { mutableStateOf(false) }
    var showEditStudioDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.homeScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.homeScrollOffset
    )

    // Continuously remember the user's exact scroll position in ViewModel
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                viewModel.homeScrollIndex = index
                viewModel.homeScrollOffset = offset
            }
    }

    // Scroll to top only when the user deliberately modifies sort, filter, or search query
    var isInitialComposition by remember { mutableStateOf(true) }
    var previousSort by remember { mutableStateOf(currentSort) }
    var previousFilter by remember { mutableStateOf(viewFilter) }
    var previousQuery by remember { mutableStateOf(searchQuery) }

    LaunchedEffect(currentSort, viewFilter, searchQuery) {
        if (isInitialComposition) {
            isInitialComposition = false
        } else if (previousSort != currentSort || previousFilter != viewFilter || previousQuery != searchQuery) {
            previousSort = currentSort
            previousFilter = viewFilter
            previousQuery = searchQuery
            viewModel.homeScrollIndex = 0
            viewModel.homeScrollOffset = 0
            if (links.isNotEmpty()) {
                listState.scrollToItem(0)
            }
        }
        activeOverlayCardId = null
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchExpanded) {
                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.searchQuery.value = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .testTag("search_scenes_input"),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search scenes, actors, studios...",
                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    } else {
                        val headerTitle = when {
                            currentScreen is ScreenState.ActorScenes || targetActor != null -> "Actor Scene"
                            currentScreen is ScreenState.StudioScenes || targetStudio != null -> "Studio Scene"
                            else -> "Goony"
                        }
                        Text(
                            text = headerTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )
                        )
                    }
                },
                navigationIcon = {
                    if (isSearchExpanded) {
                        IconButton(
                            onClick = {
                                isSearchExpanded = false
                                viewModel.searchQuery.value = ""
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close Search"
                            )
                        }
                    } else if (targetActor != null || targetStudio != null || currentScreen is ScreenState.ActorScenes || currentScreen is ScreenState.StudioScenes) {
                        IconButton(
                            onClick = { viewModel.navigateBack() },
                            modifier = Modifier.testTag("back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.testTag("open_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Drawer"
                            )
                        }
                    }
                },
                actions = {
                    if (isSearchExpanded) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.searchQuery.value = "" },
                                modifier = Modifier.testTag("clear_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    isSearchExpanded = false
                                    viewModel.searchQuery.value = ""
                                },
                                modifier = Modifier.testTag("close_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Search"
                                )
                            }
                        }
                    } else {
                        // Native Search Action
                        IconButton(
                            onClick = { isSearchExpanded = true },
                            modifier = Modifier.testTag("search_action_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }

                        // Native Sort Action (A-Z, Z-A, New, Old) with Rounded Native UI
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("sort_action_button")
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_funnel_solid),
                                    contentDescription = "Sort Mode"
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                shape = RoundedCornerShape(16.dp),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "New",
                                            fontWeight = if (currentSort == SortMode.CARD_NEWEST || currentSort == SortMode.NEWEST) FontWeight.Bold else FontWeight.Normal,
                                            color = if (currentSort == SortMode.CARD_NEWEST || currentSort == SortMode.NEWEST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = {
                                        if (currentSort == SortMode.CARD_NEWEST || currentSort == SortMode.NEWEST) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.sortMode.value = SortMode.CARD_NEWEST
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Old",
                                            fontWeight = if (currentSort == SortMode.CARD_OLDEST || currentSort == SortMode.OLDEST) FontWeight.Bold else FontWeight.Normal,
                                            color = if (currentSort == SortMode.CARD_OLDEST || currentSort == SortMode.OLDEST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = {
                                        if (currentSort == SortMode.CARD_OLDEST || currentSort == SortMode.OLDEST) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.sortMode.value = SortMode.CARD_OLDEST
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Recently Added",
                                            fontWeight = if (currentSort == SortMode.RECENTLY_ADDED) FontWeight.Bold else FontWeight.Normal,
                                            color = if (currentSort == SortMode.RECENTLY_ADDED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = {
                                        if (currentSort == SortMode.RECENTLY_ADDED) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.sortMode.value = SortMode.RECENTLY_ADDED
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Oldest Added",
                                            fontWeight = if (currentSort == SortMode.OLDEST_ADDED) FontWeight.Bold else FontWeight.Normal,
                                            color = if (currentSort == SortMode.OLDEST_ADDED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingIcon = {
                                        if (currentSort == SortMode.OLDEST_ADDED) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.sortMode.value = SortMode.OLDEST_ADDED
                                        showSortMenu = false
                                    }
                                )
                            }
                        }

                        // Edit Actor/Studio Action in Header
                        if (targetActor != null) {
                            IconButton(
                                onClick = { showEditActorDialog = true },
                                modifier = Modifier.testTag("edit_actor_header_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Actor"
                                )
                            }
                        } else if (targetStudio != null) {
                            IconButton(
                                onClick = { showEditStudioDialog = true },
                                modifier = Modifier.testTag("edit_studio_header_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Studio"
                                )
                            }
                        }

                        // Native Add Scene Action - ONLY on Main Screen (Home)
                        if (currentScreen is ScreenState.Home) {
                            IconButton(
                                onClick = { viewModel.navigateTo(ScreenState.AddEditLink()) },
                                modifier = Modifier.testTag("add_scene_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Scene"
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    ) { paddingValues ->
        // ========================================================
        // FEED LIST OF ITEMS (MATCHING SCREENSHOT LAYOUT)
        // ========================================================
        val isEntityScreen = currentScreen is ScreenState.ActorScenes || currentScreen is ScreenState.StudioScenes
        val entityName = when {
            targetActor != null -> targetActor.name
            currentScreen is ScreenState.ActorScenes -> {
                val id = (currentScreen as ScreenState.ActorScenes).actorId
                actorsMap[id] ?: id
            }
            targetStudio != null -> targetStudio.name
            currentScreen is ScreenState.StudioScenes -> {
                val id = (currentScreen as ScreenState.StudioScenes).studioId
                studiosMap[id] ?: id
            }
            else -> ""
        }
        val entityImageUrl = when {
            targetActor != null -> targetActor.imageUrl
            targetStudio != null -> targetStudio.logoUrl ?: targetStudio.imageUrl
            else -> null
        }
        val entityLogoBg = targetStudio?.logoBgColor
        val isActorEntity = currentScreen is ScreenState.ActorScenes || targetActor != null

        if (displayedLinks.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
            ) {
                if (isEntityScreen && entityName.isNotEmpty()) {
                    EntityScenesHeader(
                        name = entityName,
                        imageUrl = entityImageUrl,
                        isActor = isActorEntity,
                        logoBgColor = entityLogoBg,
                        sceneCount = 0,
                        imagePositionX = targetActor?.imagePositionX ?: 50f,
                        imagePositionY = targetActor?.imagePositionY ?: 50f,
                        imageZoom = targetActor?.imageZoom ?: 1.0f
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(60.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No results for '$searchQuery'"
                                   else if (targetActor != null) "No scenes for ${targetActor.name}"
                                   else if (targetStudio != null) "No scenes for ${targetStudio.name}"
                                   else "Vault is Empty",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try searching with different keywords"
                                   else if (targetActor != null || targetStudio != null) "Tap the '+' icon to link scenes to this entity."
                                   else "Tap the '+' icon in the top bar to add scenes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding()),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (isEntityScreen && entityName.isNotEmpty()) {
                    item(key = "entity_header") {
                        EntityScenesHeader(
                            name = entityName,
                            imageUrl = entityImageUrl,
                            isActor = isActorEntity,
                            logoBgColor = entityLogoBg,
                            sceneCount = displayedLinks.size,
                            imagePositionX = targetActor?.imagePositionX ?: 50f,
                            imagePositionY = targetActor?.imagePositionY ?: 50f,
                            imageZoom = targetActor?.imageZoom ?: 1.0f
                        )
                    }
                }

                items(displayedLinks, key = { it.id }) { link ->
                    val isBookmarked = remember(bookmarkedIds, link.id) {
                        bookmarkedIds.contains(link.id)
                    }
                    val isActive = activeOverlayCardId == link.id

                    Box(
                        modifier = Modifier.animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                            placementSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing)
                        )
                    ) {
                        LinkCard(
                            link = link,
                            actorsMap = actorsMap,
                            studiosMap = studiosMap,
                            fullActorsMap = fullActorsMap,
                            isBookmarked = isBookmarked,
                            isActiveCard = isActive,
                            onActivate = { activeOverlayCardId = link.id },
                            onDismissActive = {
                                if (activeOverlayCardId == link.id) {
                                    activeOverlayCardId = null
                                }
                            },
                            onToggleBookmark = { viewModel.toggleBookmark(link.id) },
                            onPlay = { url -> viewModel.playVideo(url, link.title, cardId = link.id) },
                            onOpenGallery = {
                                viewModel.navigateTo(ScreenState.PhotosetViewer(link.title, link.galleryUrls))
                            },
                            onEdit = {
                                viewModel.navigateTo(ScreenState.AddEditLink(link.id))
                            },
                            onDelete = {
                                viewModel.deleteLink(link.id)
                            },
                            onActorClick = { actorId ->
                                viewModel.navigateTo(ScreenState.ActorScenes(actorId))
                            },
                            onStudioClick = { studioId ->
                                viewModel.navigateTo(ScreenState.StudioScenes(studioId))
                            },
                            resolvingStatus = resolvingStatus,
                            isResolvingThisCard = resolvingCardId == link.id,
                            resolutionError = if (resolvingCardId == link.id) videoResolutionError else null,
                            onDismissResolutionError = { viewModel.dismissVideoError() },
                            inlinePlayback = if (activeInlineVideo?.cardId == link.id) activeInlineVideo else null,
                            onCloseInlineVideo = { viewModel.closeInlineVideo(link.id) },
                            onFullscreenInlineVideo = { currentPos ->
                                viewModel.openFullscreenFromInline(link.id, currentPos)
                            },
                            exoPlayer = viewModel.sharedPlayerManager.getPlayer()
                        )
                    }
                }
            }
        }
    }

    // Actor Details & Photo Adjustment Dialog (with flip/toggle button)
    if (showEditActorDialog && targetActor != null) {
        var isPhotoTab by remember { mutableStateOf(false) }
        var posX by remember { mutableFloatStateOf(targetActor.imagePositionX) }
        var posY by remember { mutableFloatStateOf(targetActor.imagePositionY) }
        var zoom by remember { mutableFloatStateOf(targetActor.imageZoom) }
        var confirmDeleteActor by remember { mutableStateOf(false) }
        val accent = LocalAccentColor.current
        val isBetaTest = LocalBetaTestPrivacy.current

        AlertDialog(
            onDismissRequest = {
                showEditActorDialog = false
                confirmDeleteActor = false
                isPhotoTab = false
            },
            shape = RoundedCornerShape(28.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPhotoTab) "Actor Photo" else "Actor Details",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(
                        onClick = { isPhotoTab = !isPhotoTab },
                        modifier = Modifier.testTag("toggle_actor_dialog_mode")
                    ) {
                        Icon(
                            imageVector = if (isPhotoTab) Icons.Default.Info else Icons.Default.Tune,
                            contentDescription = if (isPhotoTab) "Actor Details" else "Adjust Photo",
                            tint = accent
                        )
                    }
                }
            },
            text = {
                if (!isPhotoTab) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Static / Unchangeable Name Field matching Add Scene style
                        OutlinedTextField(
                            value = targetActor.name,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Name") },
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                            shape = RoundedCornerShape(32.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("actor_name_static_input")
                        )

                        // Static / Unchangeable Image URL Field matching Add Scene style
                        OutlinedTextField(
                            value = targetActor.imageUrl ?: "",
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Image URL") },
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                            shape = RoundedCornerShape(32.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("actor_image_static_input")
                        )

                        // Delete Actor and Linked Scenes Section (Circular Button)
                        if (!confirmDeleteActor) {
                            Button(
                                onClick = { confirmDeleteActor = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF4444).copy(alpha = 0.12f),
                                    contentColor = Color(0xFFEF4444)
                                ),
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("delete_actor_cascade_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Delete Actor Scene",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFEF4444).copy(alpha = 0.12f)
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Delete '${targetActor.name}' and all scenes referencing solely this actor? (Scenes with multiple actors will be preserved).",
                                        fontSize = 13.sp,
                                        color = Color(0xFFDC2626),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { confirmDeleteActor = false },
                                            shape = CircleShape
                                        ) {
                                            Text("Cancel")
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Button(
                                            onClick = {
                                                showEditActorDialog = false
                                                confirmDeleteActor = false
                                                viewModel.deleteActorWithCascade(targetActor.id)
                                                viewModel.navigateBack()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFEF4444)
                                            ),
                                            shape = CircleShape
                                        ) {
                                            Text("Confirm Delete", color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Photo Adjustment Tab
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Large Preview Circle
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(2.5.dp, accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!targetActor.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = targetActor.imageUrl,
                                    contentDescription = "Preview",
                                    contentScale = ContentScale.Crop,
                                    alignment = BiasAlignment(
                                        horizontalBias = (posX - 50f) / 50f,
                                        verticalBias = (posY - 50f) / 50f
                                    ),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .privacyImageBlur(isBetaTest)
                                        .graphicsLayer {
                                            scaleX = zoom
                                            scaleY = zoom
                                            translationX = (posX - 50f) * (zoom - 1.0f) * (size.width / 100f)
                                            translationY = (posY - 50f) * (zoom - 1.0f) * (size.height / 100f)
                                        }
                                )
                            } else {
                                ActorAvatarPlaceholder()
                            }
                        }

                        // 3 Sleek Compact Sliders (X, Y, ZOOM)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CompactCoordinateSliderRow(
                                label = "X",
                                value = posX,
                                onValueChange = { posX = it },
                                valueRange = 0f..100f,
                                valueDisplay = "${posX.toInt()}%",
                                accentColor = accent,
                                testTag = "slider_position_x"
                            )

                            CompactCoordinateSliderRow(
                                label = "Y",
                                value = posY,
                                onValueChange = { posY = it },
                                valueRange = 0f..100f,
                                valueDisplay = "${posY.toInt()}%",
                                accentColor = accent,
                                testTag = "slider_position_y"
                            )

                            CompactCoordinateSliderRow(
                                label = "ZOOM",
                                value = zoom,
                                onValueChange = { zoom = it },
                                valueRange = 1.0f..3.0f,
                                valueDisplay = String.format("%.1fx", zoom),
                                accentColor = accent,
                                testTag = "slider_zoom"
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (isPhotoTab) {
                    Button(
                        onClick = {
                            viewModel.saveActor(
                                targetActor.copy(
                                    imagePositionX = posX,
                                    imagePositionY = posY,
                                    imageZoom = zoom
                                )
                            )
                            showEditActorDialog = false
                            isPhotoTab = false
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text("Save", color = Color.White)
                    }
                }
            },
            dismissButton = {
                if (isPhotoTab) {
                    TextButton(
                        onClick = {
                            posX = 50f
                            posY = 50f
                            zoom = 1.0f
                        },
                        shape = CircleShape
                    ) {
                        Text("Reset")
                    }
                }
                TextButton(
                    onClick = {
                        showEditActorDialog = false
                        confirmDeleteActor = false
                        isPhotoTab = false
                    },
                    shape = CircleShape
                ) {
                    Text("Close")
                }
            }
        )
    }

    // Studio Details & Color Adjustment Dialog (with flip/toggle button)
    if (showEditStudioDialog && targetStudio != null) {
        var isColorTab by remember { mutableStateOf(false) }
        val initialProgress = remember(targetStudio.logoBgColor) {
            if (!targetStudio.logoBgColor.isNullOrEmpty()) {
                try {
                    val c = android.graphics.Color.parseColor(targetStudio.logoBgColor)
                    val r = android.graphics.Color.red(c)
                    val g = android.graphics.Color.green(c)
                    val b = android.graphics.Color.blue(c)
                    (r * 0.299f + g * 0.587f + b * 0.114f) / 255f
                } catch (e: Exception) {
                    0f
                }
            } else {
                0f
            }
        }
        var colorProgress by remember { mutableFloatStateOf(initialProgress) }
        var confirmDeleteStudio by remember { mutableStateOf(false) }
        val accent = LocalAccentColor.current
        val isBetaTestStudio = LocalBetaTestPrivacy.current

        val intVal = (colorProgress * 255).toInt().coerceIn(0, 255)
        val previewBgColor = remember(colorProgress) { Color(intVal, intVal, intVal) }
        val hexColor = remember(colorProgress) { String.format("#%02X%02X%02X", intVal, intVal, intVal) }

        AlertDialog(
            onDismissRequest = {
                showEditStudioDialog = false
                confirmDeleteStudio = false
                isColorTab = false
            },
            shape = RoundedCornerShape(28.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isColorTab) "Studio Color" else "Studio Details",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(
                        onClick = { isColorTab = !isColorTab },
                        modifier = Modifier.testTag("toggle_studio_dialog_mode")
                    ) {
                        Icon(
                            imageVector = if (isColorTab) Icons.Default.Info else Icons.Default.Palette,
                            contentDescription = if (isColorTab) "Studio Details" else "Adjust Color",
                            tint = accent
                        )
                    }
                }
            },
            text = {
                if (!isColorTab) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Static / Unchangeable Name Field matching Add Scene style
                        OutlinedTextField(
                            value = targetStudio.name,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Name") },
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                            shape = RoundedCornerShape(32.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("studio_name_static_input")
                        )

                        // Static / Unchangeable Image URL Field matching Add Scene style
                        OutlinedTextField(
                            value = targetStudio.logoUrl ?: "",
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Image URL") },
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                            shape = RoundedCornerShape(32.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("studio_image_static_input")
                        )

                        // Delete Studio and Linked Scenes Section (Circular Button)
                        if (!confirmDeleteStudio) {
                            Button(
                                onClick = { confirmDeleteStudio = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEF4444).copy(alpha = 0.12f),
                                    contentColor = Color(0xFFEF4444)
                                ),
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("delete_studio_cascade_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Delete Studio Scene",
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFEF4444).copy(alpha = 0.12f)
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Delete '${targetStudio.name}' and all associated scenes without exception?",
                                        fontSize = 13.sp,
                                        color = Color(0xFFDC2626),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { confirmDeleteStudio = false },
                                            shape = CircleShape
                                        ) {
                                            Text("Cancel")
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Button(
                                            onClick = {
                                                showEditStudioDialog = false
                                                confirmDeleteStudio = false
                                                viewModel.deleteStudioWithCascade(targetStudio.id)
                                                viewModel.navigateBack()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFEF4444)
                                            ),
                                            shape = CircleShape
                                        ) {
                                            Text("Confirm Delete", color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Studio Color Tab
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Large Preview Circle
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(previewBgColor)
                                .border(2.5.dp, accent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val logo = targetStudio.logoUrl ?: targetStudio.imageUrl
                            if (!logo.isNullOrBlank()) {
                                AsyncImage(
                                    model = logo,
                                    contentDescription = "Preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .privacyImageBlur(isBetaTestStudio)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_video_camera),
                                    contentDescription = null,
                                    tint = if (colorProgress > 0.5f) Color.Black else Color.White,
                                    modifier = Modifier.size(60.dp)
                                )
                            }
                        }

                        // Single Sleek Gradient Track Slider
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Background Color",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(previewBgColor)
                                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    )
                                    Text(
                                        text = hexColor,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            GradientStudioColorSlider(
                                value = colorProgress,
                                onValueChange = { colorProgress = it },
                                modifier = Modifier.testTag("slider_studio_bg_color")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Black",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                                Text(
                                    text = "White",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (isColorTab) {
                    Button(
                        onClick = {
                            viewModel.saveStudio(
                                targetStudio.copy(logoBgColor = hexColor)
                            )
                            showEditStudioDialog = false
                            isColorTab = false
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Text("Save", color = Color.White)
                    }
                }
            },
            dismissButton = {
                if (isColorTab) {
                    TextButton(
                        onClick = {
                            colorProgress = 0f
                        },
                        shape = CircleShape
                    ) {
                        Text("Reset")
                    }
                }
                TextButton(
                    onClick = {
                        showEditStudioDialog = false
                        confirmDeleteStudio = false
                        isColorTab = false
                    },
                    shape = CircleShape
                ) {
                    Text("Close")
                }
            }
        )
    }
}

/**
 * Sleek compact coordinate slider row (X, Y, ZOOM).
 */
@Composable
private fun CompactCoordinateSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueDisplay: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.width(44.dp)
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .testTag(testTag),
            contentAlignment = Alignment.CenterStart
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val thumbRadius = 8.dp
            val thumbRadiusPx = with(LocalDensity.current) { thumbRadius.toPx() }
            val availableTrackPx = (widthPx - thumbRadiusPx * 2).coerceAtLeast(1f)

            val fraction = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            val thumbOffsetPx = thumbRadiusPx + fraction * availableTrackPx

            // Subtle Background Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                // Active filled track
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(with(LocalDensity.current) { thumbOffsetPx.toDp() })
                        .background(accentColor)
                )
            }

            // Compact Round Thumb
            Box(
                modifier = Modifier
                    .offset { IntOffset((thumbOffsetPx - thumbRadiusPx).roundToInt(), 0) }
                    .size(thumbRadius * 2)
                    .shadow(2.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .border(2.dp, accentColor, CircleShape)
            )

            // Touch interaction
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(valueRange, widthPx) {
                        detectTapGestures { offset ->
                            val newFraction = ((offset.x - thumbRadiusPx) / availableTrackPx).coerceIn(0f, 1f)
                            val newValue = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                            onValueChange(newValue)
                        }
                    }
                    .pointerInput(valueRange, widthPx) {
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val newFraction = ((change.position.x - thumbRadiusPx) / availableTrackPx).coerceIn(0f, 1f)
                            val newValue = valueRange.start + newFraction * (valueRange.endInclusive - valueRange.start)
                            onValueChange(newValue)
                        }
                    }
            )
        }

        Text(
            text = valueDisplay,
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            ),
            modifier = Modifier.width(42.dp)
        )
    }
}

/**
 * Custom color slider where the slider track itself is the Black -> White gradient bar.
 */
@Composable
private fun GradientStudioColorSlider(
    value: Float, // 0f..1f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val thumbRadius = 10.dp
        val thumbRadiusPx = with(LocalDensity.current) { thumbRadius.toPx() }
        val availableTrackPx = (widthPx - thumbRadiusPx * 2).coerceAtLeast(1f)

        val fraction = value.coerceIn(0f, 1f)
        val thumbOffsetPx = thumbRadiusPx + fraction * availableTrackPx
        val intVal = (fraction * 255).toInt().coerceIn(0, 255)
        val thumbColor = remember(fraction) { Color(intVal, intVal, intVal) }

        // Gradient Track (Black -> Grey -> White)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Black, Color(0xFF777777), Color.White)
                    )
                )
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
        )

        // Custom Thumb displaying the selected color directly
        Box(
            modifier = Modifier
                .offset { IntOffset((thumbOffsetPx - thumbRadiusPx).roundToInt(), 0) }
                .size(thumbRadius * 2)
                .shadow(3.dp, CircleShape)
                .background(thumbColor, CircleShape)
                .border(2.dp, if (fraction > 0.5f) Color(0xFF1E293B) else Color.White, CircleShape)
        )

        // Touch & Drag Handling
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(widthPx) {
                    detectTapGestures { offset ->
                        val newFraction = ((offset.x - thumbRadiusPx) / availableTrackPx).coerceIn(0f, 1f)
                        onValueChange(newFraction)
                    }
                }
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val newFraction = ((change.position.x - thumbRadiusPx) / availableTrackPx).coerceIn(0f, 1f)
                        onValueChange(newFraction)
                    }
                }
        )
    }
}

/**
 * Header section shown at top of Actor Scene and Studio Scene feeds.
 * Displays the actor photo / studio logo circle and name.
 */
@Composable
private fun EntityScenesHeader(
    name: String,
    imageUrl: String?,
    isActor: Boolean,
    logoBgColor: String? = null,
    sceneCount: Int,
    imagePositionX: Float = 50f,
    imagePositionY: Float = 50f,
    imageZoom: Float = 1.0f
) {
    val accent = LocalAccentColor.current
    val isBetaTest = LocalBetaTestPrivacy.current
    val palette = LocalVaultPalette.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Circle Photo / Logo Display matching management style exactly
        val bgColor = if (isActor) {
            palette.surface
        } else {
            if (!logoBgColor.isNullOrEmpty()) {
                try {
                    Color(android.graphics.Color.parseColor(logoBgColor))
                } catch (e: Exception) {
                    palette.surface
                }
            } else {
                palette.surface
            }
        }

        val borderStroke = if (isActor) {
            BorderStroke(2.dp, accent.copy(alpha = 0.6f))
        } else {
            BorderStroke(2.dp, accent.copy(alpha = 0.5f))
        }

        val sizeDp = if (isActor) 72.dp else 70.dp

        Surface(
            shape = CircleShape,
            color = bgColor,
            shadowElevation = 3.dp,
            modifier = Modifier
                .size(sizeDp)
                .clip(CircleShape)
                .border(borderStroke, CircleShape)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = name,
                        contentScale = if (isActor) ContentScale.Crop else ContentScale.Fit,
                        alignment = if (isActor) {
                            BiasAlignment(
                                horizontalBias = (imagePositionX - 50f) / 50f,
                                verticalBias = (imagePositionY - 50f) / 50f
                            )
                        } else Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize(if (isActor) 1f else 0.8f)
                            .clip(CircleShape)
                            .privacyImageBlur(isBetaTest)
                            .graphicsLayer {
                                if (isActor) {
                                    scaleX = imageZoom
                                    scaleY = imageZoom
                                    translationX = (imagePositionX - 50f) * (imageZoom - 1.0f) * (size.width / 100f)
                                    translationY = (imagePositionY - 50f) * (imageZoom - 1.0f) * (size.height / 100f)
                                }
                            }
                    )
                } else if (isActor) {
                    ActorAvatarPlaceholder()
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_video_camera),
                        contentDescription = null,
                        tint = palette.textMuted,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // Name and Scene count
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                    letterSpacing = (-0.3).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$sceneCount ${if (sceneCount == 1) "scene" else "scenes"}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

