package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.components.ActorAvatarPlaceholder
import com.example.ui.components.ShimmerBrush
import com.example.ui.components.SmoothProgressIndicator
import com.example.data.local.entity.ActorEntity
import com.example.data.local.entity.LinkEntity
import com.example.data.local.entity.StudioEntity
import com.example.network.StashDbApiService
import com.example.network.StashPerformer
import com.example.network.StashScene
import com.example.network.StashStudio
import com.example.ui.MainViewModel
import com.example.ui.ScreenState
import com.example.ui.theme.LocalAccentColor
import com.example.ui.theme.LocalBetaTestPrivacy
import com.example.ui.theme.LocalVaultPalette
import com.example.ui.theme.privacyImageBlur
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

import com.example.ui.StashSearchType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StashDbScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var shouldRequestSearchFocus by remember { mutableStateOf(false) }

    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val savedLinks by viewModel.allLinks.collectAsStateWithLifecycle()
    val savedStashDbIds by remember(savedLinks) {
        derivedStateOf { savedLinks.mapNotNull { it.stashDbId }.toSet() }
    }
    val savedTitles by remember(savedLinks) {
        derivedStateOf { savedLinks.map { it.title.trim().lowercase() }.toSet() }
    }

    // Persistent state from MainViewModel
    val searchQuery by viewModel.stashSearchQuery.collectAsStateWithLifecycle()
    val activeType by viewModel.stashActiveType.collectAsStateWithLifecycle()
    val isSearchExpanded by viewModel.isStashSearchExpanded.collectAsStateWithLifecycle()

    val performerResults by viewModel.stashPerformerResults.collectAsStateWithLifecycle()
    val studioResults by viewModel.stashStudioResults.collectAsStateWithLifecycle()

    val selectedPerformer by viewModel.stashSelectedPerformer.collectAsStateWithLifecycle()
    val selectedStudio by viewModel.stashSelectedStudio.collectAsStateWithLifecycle()

    val scenesList by viewModel.stashScenesList.collectAsStateWithLifecycle()
    val selectedSceneIds by viewModel.stashSelectedSceneIds.collectAsStateWithLifecycle()

    val isSearchingTarget by viewModel.isStashLoadingEntities.collectAsStateWithLifecycle()
    val isLoadingScenes by viewModel.isStashLoadingScenes.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isStashLoadingMore.collectAsStateWithLifecycle()
    val canLoadMore by viewModel.stashCanLoadMore.collectAsStateWithLifecycle()
    val searchError by viewModel.stashSearchError.collectAsStateWithLifecycle()
    val totalScenesCount by viewModel.stashTotalScenesCount.collectAsStateWithLifecycle()

    // System Back Press Handling
    BackHandler {
        if (selectedSceneIds.isNotEmpty()) {
            viewModel.clearStashSelection()
        } else if (isSearchExpanded) {
            shouldRequestSearchFocus = false
            focusManager.clearFocus()
            viewModel.setStashSearchExpanded(false)
            viewModel.setStashSearchQuery("")
        } else {
            viewModel.navigateTo(ScreenState.Home)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Track scene IDs that have already played their entrance animation to avoid repeating during scroll
    val animatedSceneIds = remember { mutableSetOf<String>() }

    LaunchedEffect(selectedPerformer?.id, selectedStudio?.id) {
        animatedSceneIds.clear()
    }

    // Grid state and smooth scroll-driven visibility for the horizontal results row
    val gridState = rememberLazyGridState()
    var isHorizontalResultsVisible by remember { mutableStateOf(true) }

    val density = LocalDensity.current
    val scrollThresholdPx = remember(density) { with(density) { 48.dp.toPx() } }
    var scrollAccumulator by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember(scrollThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0f) {
                    if (scrollAccumulator > 0f) scrollAccumulator = 0f
                    scrollAccumulator += delta
                    if (scrollAccumulator < -scrollThresholdPx && isHorizontalResultsVisible) {
                        isHorizontalResultsVisible = false
                        scrollAccumulator = 0f
                    }
                } else if (delta > 0f) {
                    if (scrollAccumulator < 0f) scrollAccumulator = 0f
                    scrollAccumulator += delta
                    if (scrollAccumulator > scrollThresholdPx && !isHorizontalResultsVisible) {
                        isHorizontalResultsVisible = true
                        scrollAccumulator = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }

    // Dynamic scroll observation: restore visibility whenever list returns to top
    val isAtTop by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset <= 4
        }
    }

    LaunchedEffect(isAtTop) {
        if (isAtTop) {
            isHorizontalResultsVisible = true
            scrollAccumulator = 0f
        }
    }

    // Reset visibility on selection change or mode change
    LaunchedEffect(selectedPerformer, selectedStudio, activeType) {
        isHorizontalResultsVisible = true
        scrollAccumulator = 0f
    }

    // Trigger loading more when scrolling near bottom
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = scenesList.size
            if (totalItems == 0 || isLoadingScenes || isLoadingMore || !canLoadMore) {
                false
            } else {
                val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleItem >= totalItems - 6
            }
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMoreStashScenes(settings.stashDbApiKey)
        }
    }

    // Save all selected scenes to Links with batch DB insert
    val saveSelectedScenes = {
        viewModel.saveSelectedStashScenes { savedCount ->
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "Saved $savedCount scene${if (savedCount > 1) "s" else ""} to Links!",
                    actionLabel = "View",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.navigateTo(ScreenState.Home)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = palette.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AnimatedContent(
                targetState = selectedSceneIds.isNotEmpty(),
                transitionSpec = {
                    fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
                },
                label = "top_app_bar_mode"
            ) { isContextual ->
                if (isContextual) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "${selectedSceneIds.size} selected",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = palette.textPrimary
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.clearStashSelection() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear selection",
                                    tint = palette.textPrimary
                                )
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    val unsavedIds = scenesList
                                        .filter { scene ->
                                            val isSaved = (scene.id in savedStashDbIds) || (scene.title.trim().lowercase() in savedTitles)
                                            !isSaved
                                        }
                                        .map { it.id }
                                    viewModel.selectAllStashScenes(unsavedIds)
                                }
                            ) {
                                Text(
                                    text = "Select all",
                                    color = accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(
                                onClick = { saveSelectedScenes() },
                                modifier = Modifier.testTag("save_selected_scenes_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Save ${selectedSceneIds.size}",
                                    tint = accent
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = palette.surface,
                            titleContentColor = palette.textPrimary
                        )
                    )
                } else {
                    TopAppBar(
                        title = {
                            if (isSearchExpanded) {
                                LaunchedEffect(shouldRequestSearchFocus) {
                                    if (shouldRequestSearchFocus) {
                                        focusRequester.requestFocus()
                                        shouldRequestSearchFocus = false
                                    }
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.setStashSearchQuery(it) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = palette.textPrimary,
                                        fontSize = 15.sp
                                    ),
                                    cursorBrush = SolidColor(accent),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(
                                        onSearch = {
                                            focusManager.clearFocus()
                                            viewModel.performStashSearch(settings.stashDbApiKey, searchQuery)
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester)
                                        .testTag("stashdb_header_search_input"),
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (searchQuery.isEmpty()) {
                                                Text(
                                                    text = if (activeType == StashSearchType.ACTORS) "Search female actors..." else "Search studios...",
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        fontSize = 15.sp,
                                                        color = palette.textMuted
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
                                val selectedTitle = selectedPerformer?.name ?: selectedStudio?.name ?: "StashDB"
                                Text(
                                    text = selectedTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    color = palette.textPrimary
                                )
                            }
                        },
                        navigationIcon = {
                            if (isSearchExpanded) {
                                IconButton(
                                    onClick = {
                                        shouldRequestSearchFocus = false
                                        focusManager.clearFocus()
                                        viewModel.setStashSearchExpanded(false)
                                        viewModel.setStashSearchQuery("")
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Close Search",
                                        tint = palette.textPrimary
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { viewModel.navigateTo(ScreenState.Home) },
                                    modifier = Modifier.testTag("back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = palette.textPrimary
                                    )
                                }
                            }
                        },
                        actions = {
                            if (isSearchExpanded) {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            focusManager.clearFocus()
                                            viewModel.performStashSearch(settings.stashDbApiKey, searchQuery)
                                        },
                                        modifier = Modifier.testTag("stashdb_header_search_submit")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search",
                                            tint = accent
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.setStashSearchQuery("") },
                                        modifier = Modifier.testTag("clear_search_text_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear text",
                                            tint = palette.textSecondary
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            shouldRequestSearchFocus = false
                                            focusManager.clearFocus()
                                            viewModel.setStashSearchExpanded(false)
                                            viewModel.setStashSearchQuery("")
                                        },
                                        modifier = Modifier.testTag("close_search_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close Search",
                                            tint = palette.textSecondary
                                        )
                                    }
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        shouldRequestSearchFocus = true
                                        viewModel.setStashSearchExpanded(true)
                                    },
                                    modifier = Modifier.testTag("stashdb_search_action_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = palette.textPrimary
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = palette.surface,
                            titleContentColor = palette.textPrimary
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Warning Banner: Missing StashDB API Key
                if (settings.stashDbApiKey.isBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "API Key Warning",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "StashDB API Key Missing",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = "Add your key in Settings to search actors and scenes.",
                                    fontSize = 11.sp,
                                    color = palette.textSecondary
                                )
                            }
                            FilledTonalButton(
                                onClick = { viewModel.navigateTo(ScreenState.Settings) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Settings", fontSize = 11.5.sp)
                            }
                        }
                    }
                } else if (searchError != null) {
                    // Search Error Banner
                    Surface(
                        color = palette.surface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = searchError ?: "Search failed",
                                fontSize = 11.5.sp,
                                color = palette.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    val perf = selectedPerformer
                                    val stud = selectedStudio
                                    if (perf != null) {
                                        viewModel.selectStashPerformer(perf, settings.stashDbApiKey)
                                    } else if (stud != null) {
                                        viewModel.selectStashStudio(stud, settings.stashDbApiKey)
                                    } else if (searchQuery.isNotBlank()) {
                                        viewModel.performStashSearch(settings.stashDbApiKey, searchQuery)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = "Retry",
                                    fontSize = 12.sp,
                                    color = accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(
                                onClick = { viewModel.clearStashSearchError() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss error",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Two Mode Selector Tabs: Actors & Studio
                Surface(
                    color = palette.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val tabWidth = maxWidth / 2
                            val isActorsSelected = activeType == StashSearchType.ACTORS
                            val isStudioSelected = activeType == StashSearchType.STUDIO
                            val indicatorOffset by animateDpAsState(
                                targetValue = if (isActorsSelected) 0.dp else tabWidth,
                                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                                label = "tab_indicator_offset"
                            )

                            val actorsInteractionSource = remember { MutableInteractionSource() }
                            val isActorsPressed by actorsInteractionSource.collectIsPressedAsState()
                            val actorsScale by animateFloatAsState(
                                targetValue = if (isActorsPressed) 0.96f else 1f,
                                animationSpec = tween(100),
                                label = "actors_tab_scale"
                            )

                            val studioInteractionSource = remember { MutableInteractionSource() }
                            val isStudioPressed by studioInteractionSource.collectIsPressedAsState()
                            val studioScale by animateFloatAsState(
                                targetValue = if (isStudioPressed) 0.96f else 1f,
                                animationSpec = tween(100),
                                label = "studio_tab_scale"
                            )

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    // Actors Tab
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .graphicsLayer {
                                                scaleX = actorsScale
                                                scaleY = actorsScale
                                            }
                                            .selectable(
                                                selected = isActorsSelected,
                                                role = Role.Tab,
                                                interactionSource = actorsInteractionSource,
                                                indication = null,
                                                onClick = { viewModel.setStashActiveType(StashSearchType.ACTORS, settings.stashDbApiKey) }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.RecentActors,
                                                contentDescription = null,
                                                tint = if (isActorsSelected) accent else palette.textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Actors",
                                                fontWeight = if (isActorsSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (isActorsSelected) accent else palette.textSecondary
                                            )
                                        }
                                    }

                                    // Studio Tab
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .graphicsLayer {
                                                scaleX = studioScale
                                                scaleY = studioScale
                                            }
                                            .selectable(
                                                selected = isStudioSelected,
                                                role = Role.Tab,
                                                interactionSource = studioInteractionSource,
                                                indication = null,
                                                onClick = { viewModel.setStashActiveType(StashSearchType.STUDIO, settings.stashDbApiKey) }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MovieCreation,
                                                contentDescription = null,
                                                tint = if (isStudioSelected) accent else palette.textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Studio",
                                                fontWeight = if (isStudioSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = if (isStudioSelected) accent else palette.textSecondary
                                            )
                                        }
                                    }
                                }

                                // Animated sliding indicator
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .background(palette.border.copy(alpha = 0.2f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = indicatorOffset)
                                            .width(tabWidth)
                                            .fillMaxHeight()
                                            .background(accent)
                                    )
                                }
                            }
                        }
                        HorizontalDivider(color = palette.border)
                    }
                }

                // Horizontal Results Row with Smooth Native Scroll-Driven Collapse
                AnimatedVisibility(
                    visible = isHorizontalResultsVisible && (performerResults.isNotEmpty() || studioResults.isNotEmpty() || isSearchingTarget),
                    enter = expandVertically(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        )
                    ) + fadeIn(animationSpec = tween(150)),
                    exit = shrinkVertically(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        )
                    ) + fadeOut(animationSpec = tween(150))
                ) {
                    if (isSearchingTarget) {
                        val shimmerBrush = ShimmerBrush()
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(72.dp)
                                        .height(11.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(shimmerBrush)
                                )
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalFadeEdge(24.dp),
                                userScrollEnabled = false
                            ) {
                                items(6) {
                                    StashCircleSkeletonItem()
                                }
                            }
                        }
                    } else if (activeType == StashSearchType.ACTORS && performerResults.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(
                                text = "Results : ${performerResults.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = palette.textMuted,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalFadeEdge(24.dp)
                            ) {
                                items(performerResults, key = { it.id }) { performer ->
                                    val isSelected = selectedPerformer?.id == performer.id
                                    HorizontalActorCircleItem(
                                        performer = performer,
                                        isSelected = isSelected,
                                        onClick = { viewModel.selectStashPerformer(performer, settings.stashDbApiKey) }
                                    )
                                }
                            }
                        }
                    } else if (activeType == StashSearchType.STUDIO && studioResults.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(
                                text = "Results : ${studioResults.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = palette.textMuted,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalFadeEdge(24.dp)
                            ) {
                                items(studioResults, key = { it.id }) { studio ->
                                    val isSelected = selectedStudio?.id == studio.id
                                    HorizontalStudioCircleItem(
                                        studio = studio,
                                        isSelected = isSelected,
                                        onClick = { viewModel.selectStashStudio(studio, settings.stashDbApiKey) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Main Content: Adaptive Columns Grid / Skeleton
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val columns = if (maxWidth < 600.dp) 2 else (maxWidth / 180.dp).toInt().coerceAtLeast(2)
                    val bottomInsetPadding = 10.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                    if (isLoadingScenes) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = bottomInsetPadding),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = false
                        ) {
                            items(6) {
                                StashSceneCardSkeleton()
                            }
                        }
                    } else if (scenesList.isNotEmpty()) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            state = gridState,
                            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = bottomInsetPadding),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(nestedScrollConnection)
                        ) {
                            items(scenesList, key = { it.id }) { scene ->
                                val isSelected = selectedSceneIds.contains(scene.id)
                                val isAlreadySaved = (scene.id in savedStashDbIds) || (scene.title.trim().lowercase() in savedTitles)
                                val playEntrance = scene.id !in animatedSceneIds
                                if (playEntrance) {
                                    animatedSceneIds.add(scene.id)
                                }

                                StashGridPhotoCard(
                                    scene = scene,
                                    isSelected = isSelected,
                                    isAlreadySaved = isAlreadySaved,
                                    playEntrance = playEntrance,
                                    onToggleSelect = {
                                        viewModel.toggleStashSceneSelection(scene.id)
                                    }
                                )
                            }

                            if (isLoadingMore) {
                                item(span = { GridItemSpan(columns) }) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SmoothProgressIndicator(
                                            color = accent,
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Loading more scenes...",
                                            color = palette.textSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else if ((selectedPerformer != null || selectedStudio != null) && !isLoadingScenes && scenesList.isEmpty()) {
                        val selectedName = selectedPerformer?.name ?: selectedStudio?.name.orEmpty()
                        EmptyStateView(
                            icon = Icons.Default.SearchOff,
                            title = "No scenes found for $selectedName",
                            subtitle = "Select another result from the top row or try a new search query."
                        )
                    } else if (searchQuery.isNotBlank() && performerResults.isEmpty() && studioResults.isEmpty() && !isSearchingTarget) {
                        EmptyStateView(
                            icon = Icons.Default.SearchOff,
                            title = "No Scenes Available",
                            subtitle = "Select another result from the top row or try a new search query."
                        )
                    } else {
                        EmptyStateView(
                            icon = if (activeType == StashSearchType.ACTORS) Icons.Default.RecentActors else Icons.Default.MovieCreation,
                            title = if (activeType == StashSearchType.ACTORS) "Search Actors & Explore Scenes" else "Search Studio & Explore Scenes",
                            subtitle = "Tap the search icon in the header, type a name, and tap the search icon to search. Click any scene to select, then tap the checkmark in the header to save."
                        )
                    }
                }
            }
        }
    }
}

/**
 * Generic circular item for actors and studios in the horizontal results row.
 */
@Composable
private fun StashCircleItem(
    name: String,
    isSelected: Boolean,
    testTag: String,
    circleBackground: Color,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current

    val circleScale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "stash_circle_scale"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "stash_circle_press_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .size(60.dp)
                .graphicsLayer {
                    scaleX = circleScale * pressScale
                    scaleY = circleScale * pressScale
                }
                .border(
                    BorderStroke(
                        if (isSelected) 2.5.dp else 1.2.dp,
                        if (isSelected) accent else palette.border.copy(alpha = 0.6f)
                    ),
                    CircleShape
                )
                .clip(CircleShape)
                .background(circleBackground),
            contentAlignment = Alignment.Center,
            content = content
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = name,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) accent else palette.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Circular Item for Actor displayed in the horizontal row (Circle on top, Name below)
 */
@Composable
fun HorizontalActorCircleItem(
    performer: StashPerformer,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val context = LocalContext.current
    val isBetaTest = LocalBetaTestPrivacy.current
    val shimmerBrush = ShimmerBrush()

    val imageRequest = remember(performer.imageUrl) {
        if (!performer.imageUrl.isNullOrBlank()) {
            ImageRequest.Builder(context)
                .data(performer.imageUrl)
                .crossfade(200)
                .build()
        } else null
    }

    StashCircleItem(
        name = performer.name,
        isSelected = isSelected,
        testTag = "stash_actor_${performer.id}",
        circleBackground = palette.cardBg,
        onClick = onClick
    ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = performer.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .background(shimmerBrush)
                    .privacyImageBlur(isBetaTest)
            )
            if (isBetaTest) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f))
                )
            }
        } else {
            ActorAvatarPlaceholder()
        }
    }
}

/**
 * Circular Item for Studio displayed in the horizontal row (Circle on top, Name below)
 * Displays StashDB studio PNG logos on a solid AMOLED dark background (Color(0xFF0F0F12))
 * for seamless integration as a unified image, adapting to cardBg in light theme.
 */
@Composable
fun HorizontalStudioCircleItem(
    studio: StashStudio,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current
    val context = LocalContext.current
    val isBetaTest = LocalBetaTestPrivacy.current
    val shimmerBrush = ShimmerBrush()

    val studioCircleBg = if (palette.bg.luminance() > 0.5f) {
        palette.cardBg
    } else {
        Color(0xFF0F0F12)
    }

    val formattedLogoUrl = remember(studio.logoUrl) {
        studio.logoUrl?.trim()?.replace("http://", "https://")
    }

    val imageRequest = remember(formattedLogoUrl) {
        if (!formattedLogoUrl.isNullOrBlank()) {
            ImageRequest.Builder(context)
                .data(formattedLogoUrl)
                .crossfade(200)
                .build()
        } else null
    }

    StashCircleItem(
        name = studio.name,
        isSelected = isSelected,
        testTag = "stash_studio_${studio.id}",
        circleBackground = studioCircleBg,
        onClick = onClick
    ) {
        if (imageRequest != null) {
            var isImageError by remember(formattedLogoUrl) { mutableStateOf(false) }
            if (!isImageError) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = studio.name,
                    contentScale = ContentScale.Fit,
                    onError = { isImageError = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(shimmerBrush)
                        .padding(8.dp)
                        .privacyImageBlur(isBetaTest)
                )
                if (isBetaTest) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f))
                    )
                }
            } else {
                StudioFallbackEmblem(name = studio.name, accentColor = accent)
            }
        } else {
            StudioFallbackEmblem(name = studio.name, accentColor = accent)
        }
    }
}

/**
 * 2-Cards-Per-Row Scene Card matching the exact screenshot layout:
 * - Smooth entrance slide-up + fade-in animation
 * - Rounded corners (16.dp)
 * - Cover image (16:9)
 * - Dimmed/desaturated image with circular check badge when selected
 * - Bold title (1 line with ellipsis)
 * - Subtle divider
 * - 3 metadata rows with outlined icons (Person, Studio Logo/Videocam, CalendarToday)
 */
@Composable
fun StashGridPhotoCard(
    scene: StashScene,
    isSelected: Boolean,
    isAlreadySaved: Boolean = false,
    playEntrance: Boolean = true,
    onToggleSelect: () -> Unit
) {
    val palette = LocalVaultPalette.current
    val accent = LocalAccentColor.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val shimmerBrush = ShimmerBrush()
    val uPath = remember { Path() }

    val grayscaleFilter = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.0f) })
    }

    val initialTranslationYPx = remember(density) { with(density) { 12.dp.toPx() } }

    // Smooth entrance animation state when card loads into view
    var isCardVisible by remember { mutableStateOf(!playEntrance) }
    LaunchedEffect(scene.id) {
        if (playEntrance) {
            isCardVisible = true
        }
    }

    val animatedCardAlpha by animateFloatAsState(
        targetValue = if (isCardVisible) 1.0f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "card_entrance_alpha"
    )

    val animatedCardTranslationY by animateFloatAsState(
        targetValue = if (isCardVisible) 0f else initialTranslationYPx,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "card_entrance_translation"
    )

    val cardSelectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.978f else 1.0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "card_select_scale"
    )

    val cardInteractionSource = remember { MutableInteractionSource() }
    val isPressed by cardInteractionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "card_press_scale"
    )

    val selectionProgress by animateFloatAsState(
        targetValue = if (isSelected) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "card_select_progress"
    )

    val isBetaTest = LocalBetaTestPrivacy.current

    val stateDesc = when {
        isAlreadySaved -> "Already saved"
        isSelected -> "Selected"
        else -> "Not selected"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .graphicsLayer {
                alpha = animatedCardAlpha
                translationY = animatedCardTranslationY
                scaleX = cardSelectionScale * pressScale
                scaleY = cardSelectionScale * pressScale
            }
            .toggleable(
                value = isSelected,
                role = Role.Checkbox,
                interactionSource = cardInteractionSource,
                indication = null,
                onValueChange = { onToggleSelect() }
            )
            .semantics {
                stateDescription = stateDesc
            }
            .testTag("stash_scene_${scene.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = null
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Cover Image Box: Fixed 16:9 aspect ratio, shimmer brush background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(shimmerBrush),
                contentAlignment = Alignment.Center
            ) {
                if (!scene.coverUrl.isNullOrBlank()) {
                    val imageRequest = remember(scene.coverUrl) {
                        ImageRequest.Builder(context)
                            .data(scene.coverUrl)
                            .crossfade(200)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = scene.title,
                        contentScale = ContentScale.Crop,
                        colorFilter = if (isAlreadySaved) grayscaleFilter else null,
                        modifier = Modifier
                            .fillMaxSize()
                            .privacyImageBlur(isBetaTest)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = palette.textMuted,
                        modifier = Modifier.size(40.dp)
                    )
                }

                if (isBetaTest && !scene.coverUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f))
                    )
                }

                // Smooth lightweight dimmed overlay on selection only
                val overlayAlpha = if (isSelected) 0.32f else 0.0f
                if (overlayAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = overlayAlpha))
                    )
                }

                // Small "Saved" pill badge at top-start for already saved scenes
                if (isAlreadySaved) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.68f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Already saved",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Saved",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Circular Check badge in top right for selected scenes (smooth, fast & light)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSelected,
                        enter = fadeIn(animationSpec = tween(160)) + scaleIn(
                            animationSpec = tween(180, easing = FastOutSlowInEasing),
                            initialScale = 0.6f
                        ),
                        exit = fadeOut(animationSpec = tween(120)) + scaleOut(
                            animationSpec = tween(120, easing = FastOutSlowInEasing),
                            targetScale = 0.6f
                        )
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accent,
                            shadowElevation = 3.dp,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Card Content: Custom U-shape border with smooth animated progress
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = palette.surface,
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                    )
                    .drawBehind {
                        val cornerRadiusPx = 16.dp.toPx()
                        val strokeWidth = (0.6f + 1.4f * selectionProgress).dp.toPx()
                        val halfStroke = strokeWidth / 2f

                        uPath.reset()
                        uPath.moveTo(halfStroke, 0f)
                        uPath.lineTo(halfStroke, size.height - cornerRadiusPx)
                        uPath.arcTo(
                            rect = Rect(
                                left = halfStroke,
                                top = size.height - 2 * cornerRadiusPx + halfStroke,
                                right = 2 * cornerRadiusPx - halfStroke,
                                bottom = size.height - halfStroke
                            ),
                            startAngleDegrees = 180f,
                            sweepAngleDegrees = -90f,
                            forceMoveTo = false
                        )
                        uPath.lineTo(size.width - cornerRadiusPx, size.height - halfStroke)
                        uPath.arcTo(
                            rect = Rect(
                                left = size.width - 2 * cornerRadiusPx + halfStroke,
                                top = size.height - 2 * cornerRadiusPx + halfStroke,
                                right = size.width - halfStroke,
                                bottom = size.height - halfStroke
                            ),
                            startAngleDegrees = 90f,
                            sweepAngleDegrees = -90f,
                            forceMoveTo = false
                        )
                        uPath.lineTo(size.width - halfStroke, 0f)

                        val defaultBorderColor = palette.border.copy(alpha = 0.18f)
                        val borderBrush = Brush.verticalGradient(
                            0.0f to lerp(defaultBorderColor, accent.copy(alpha = 0.12f), selectionProgress),
                            0.45f to lerp(defaultBorderColor, accent.copy(alpha = 0.65f), selectionProgress),
                            1.0f to lerp(defaultBorderColor, accent, selectionProgress),
                            startY = 0f,
                            endY = size.height
                        )

                        drawPath(
                            path = uPath,
                            brush = borderBrush,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Title (Bold, 1 line with ellipsis)
                Text(
                    text = scene.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Divider line below title
                HorizontalDivider(
                    color = palette.border.copy(alpha = 0.35f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 1.dp)
                )

                // Row 1: Person Outline Icon + Actors
                val actorText = if (scene.femalePerformers.isNotEmpty()) {
                    scene.femalePerformers.joinToString(", ") { it.name }
                } else {
                    "No performers"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = actorText,
                        fontSize = 11.5.sp,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Row 2: Studio Videocam Icon + Studio Name
                val studioText = scene.studioName ?: "Unknown studio"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Videocam,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(13.5.dp)
                    )
                    Text(
                        text = studioText,
                        fontSize = 11.5.sp,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Row 3: Calendar Outline Icon + Date
                val dateText = remember(scene.date) {
                    val raw = scene.date
                    if (raw.isNullOrBlank()) {
                        "No date"
                    } else {
                        try {
                            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                            val parsed = parser.parse(raw)
                            if (parsed != null) formatter.format(parsed) else raw
                        } catch (_: Exception) {
                            raw
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = dateText,
                        fontSize = 11.5.sp,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    val palette = LocalVaultPalette.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.textMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(54.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = palette.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Fallback emblem for studios when logo URL is missing or fails to load.
 * Displays stylized studio initials on a dark AMOLED gradient.
 */
@Composable
private fun StudioFallbackEmblem(name: String, accentColor: Color) {
    val initials = name.trim().split(" ", "-", "_")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "S" }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(accentColor.copy(alpha = 0.35f), Color(0xFF0F0F12))
                )
            )
    ) {
        Text(
            text = initials,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Lightweight, GPU-accelerated horizontal fade mask for smooth gradient edge aesthetic.
 */
private fun Modifier.horizontalFadeEdge(fadeWidth: Dp = 24.dp): Modifier = this.then(
    Modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fadePx = fadeWidth.toPx()
            if (size.width > fadePx * 2 && fadePx > 0f) {
                val leftFraction = (fadePx / size.width).coerceIn(0f, 0.49f)
                val rightFraction = 1f - leftFraction
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        leftFraction to Color.Black,
                        rightFraction to Color.Black,
                        1f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
        }
)

/**
 * Skeleton placeholder card for loading scenes matching StashGridPhotoCard 1:1.
 */
@Composable
private fun StashSceneCardSkeleton() {
    val palette = LocalVaultPalette.current
    val shimmerBrush = ShimmerBrush()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(0.6.dp, palette.border.copy(alpha = 0.2f)),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        border = null
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 16:9 Shimmer Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(shimmerBrush)
            )

            // Content matching StashGridPhotoCard layout 1:1
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = palette.surface,
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Title line (Bold 13sp equivalent)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )

                // Divider line below title
                HorizontalDivider(
                    color = palette.border.copy(alpha = 0.35f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 1.dp)
                )

                // Row 1: Person Outline Icon + Actors
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(shimmerBrush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(11.5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(shimmerBrush)
                    )
                }

                // Row 2: Studio Videocam Icon + Studio Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(13.5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(shimmerBrush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.48f)
                            .height(11.5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(shimmerBrush)
                    )
                }

                // Row 3: Calendar Outline Icon + Date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(shimmerBrush)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(11.5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(shimmerBrush)
                    )
                }
            }
        }
    }
}

/**
 * Skeleton placeholder item for loading actors or studios circle list.
 */
@Composable
private fun StashCircleSkeletonItem() {
    val shimmerBrush = ShimmerBrush()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .size(60.dp)
                .clip(CircleShape)
                .background(shimmerBrush)
        )

        Spacer(modifier = Modifier.height(5.dp))

        Box(
            modifier = Modifier
                .width(52.dp)
                .height(11.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush)
        )
    }
}

