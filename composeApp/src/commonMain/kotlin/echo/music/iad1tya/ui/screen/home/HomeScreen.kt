package echo.music.iad1tya.ui.screen.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import echo.music.iad1tya.common.CHART_SUPPORTED_COUNTRY
import echo.music.iad1tya.common.Config
import echo.music.iad1tya.domain.data.model.browse.album.Track
import echo.music.iad1tya.domain.data.model.home.HomeItem
import echo.music.iad1tya.domain.data.model.home.chart.Chart
import echo.music.iad1tya.domain.data.model.mood.Mood
import echo.music.iad1tya.domain.mediaservice.handler.PlaylistType
import echo.music.iad1tya.domain.mediaservice.handler.QueueData
import echo.music.iad1tya.domain.utils.toSongEntity
import echo.music.iad1tya.domain.utils.toTrack
import echo.music.iad1tya.ui.component.rememberHolderPainter
import echo.music.iad1tya.extension.isScrollingUp
import echo.music.iad1tya.ui.component.Chip
import echo.music.iad1tya.ui.component.DropdownButton
import echo.music.iad1tya.ui.component.HomeItem
import echo.music.iad1tya.ui.component.HomeItemContentPlaylist
import echo.music.iad1tya.ui.component.ItemArtistChart
import echo.music.iad1tya.ui.component.MoodMomentAndGenreHomeItem
import echo.music.iad1tya.ui.component.NowPlayingBottomSheet
import echo.music.iad1tya.ui.component.QuickPicksItem
import echo.music.iad1tya.ui.icon.History
import echo.music.iad1tya.ui.icon.Settings
import echo.music.iad1tya.ui.icon.echoIcons
import echo.music.iad1tya.ui.icon.Menu
import echo.music.iad1tya.ui.navigation.destination.home.HomeDestination
import echo.music.iad1tya.ui.navigation.destination.home.MoodDestination
import echo.music.iad1tya.ui.navigation.destination.home.RecentlySongsDestination
import echo.music.iad1tya.ui.navigation.destination.home.SettingsDestination
import echo.music.iad1tya.ui.navigation.destination.list.ArtistDestination
import echo.music.iad1tya.ui.navigation.destination.list.PlaylistDestination
import echo.music.iad1tya.ui.theme.typo
import echo.music.iad1tya.viewModel.HomeViewModel
import echo.music.iad1tya.viewModel.HomeViewModel.Companion.HOME_PARAMS_COMMUTE
import echo.music.iad1tya.viewModel.HomeViewModel.Companion.HOME_PARAMS_ENERGIZE
import echo.music.iad1tya.viewModel.HomeViewModel.Companion.HOME_PARAMS_FEEL_GOOD
import echo.music.iad1tya.viewModel.HomeViewModel.Companion.HOME_PARAMS_FOCUS
import echo.music.iad1tya.viewModel.HomeViewModel.Companion.HOME_PARAMS_MIX_FOR_YOU
import echo.music.iad1tya.viewModel.HomeViewModel.Companion.HOME_PARAMS_PARTY
import echo.music.iad1tya.viewModel.HomeViewModel.Companion.HOME_PARAMS_RELAX
import echo.music.iad1tya.viewModel.HomeViewModel.Companion.HOME_PARAMS_ROMANCE
import echo.music.iad1tya.viewModel.HomeViewModel.Companion.HOME_PARAMS_SAD
import echo.music.iad1tya.viewModel.HomeViewModel.Companion.HOME_PARAMS_SLEEP
import echo.music.iad1tya.viewModel.LibraryViewModel
import echomusic.composeapp.generated.resources.mix_for_you
import echomusic.composeapp.generated.resources.no_mixes_found
import echo.music.iad1tya.viewModel.HomeViewModel.Companion.HOME_PARAMS_WORKOUT
import echo.music.iad1tya.viewModel.ListState
import echo.music.iad1tya.viewModel.SharedViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import echomusic.composeapp.generated.resources.Res
import echomusic.composeapp.generated.resources.all
import echomusic.composeapp.generated.resources.app_name
import echomusic.composeapp.generated.resources.chart
import echomusic.composeapp.generated.resources.commute
import echomusic.composeapp.generated.resources.energize
import echomusic.composeapp.generated.resources.feel_good
import echomusic.composeapp.generated.resources.focus
import echomusic.composeapp.generated.resources.let_s_pick_a_playlist_for_you
import echomusic.composeapp.generated.resources.let_s_start_with_a_radio
import echomusic.composeapp.generated.resources.party
import echomusic.composeapp.generated.resources.quick_picks
import echomusic.composeapp.generated.resources.relax
import echomusic.composeapp.generated.resources.romance
import echomusic.composeapp.generated.resources.sad
import echomusic.composeapp.generated.resources.sleep
import echomusic.composeapp.generated.resources.top_artists
import echomusic.composeapp.generated.resources.welcome_back
import echomusic.composeapp.generated.resources.what_is_best_choice_today
import echomusic.composeapp.generated.resources.workout


private val listOfHomeChip =
    listOf(
        Res.string.all,
        Res.string.mix_for_you,
        Res.string.relax,
        Res.string.sleep,
        Res.string.energize,
        Res.string.sad,
        Res.string.romance,
        Res.string.feel_good,
        Res.string.workout,
        Res.string.party,
        Res.string.commute,
        Res.string.focus,
    )

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalFoundationApi
@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    onScrolling: (onTop: Boolean) -> Unit = {},
    viewModel: HomeViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = koinInject(),
    libraryViewModel: LibraryViewModel = koinViewModel(),
    navController: NavController,
) {
    val homeData by viewModel.homeItemList.collectAsStateWithLifecycle()
    val newRelease by viewModel.newRelease.collectAsStateWithLifecycle()
    val accountInfo by viewModel.accountInfo.collectAsStateWithLifecycle()
    val recentSongs by viewModel.recentSongs.collectAsStateWithLifecycle()
    val playlists by viewModel.localPlaylists.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlayingVideoId.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val params by viewModel.params.collectAsStateWithLifecycle()
    val homeListState by viewModel.homeListState.collectAsStateWithLifecycle()
    val continuation by viewModel.continuation.collectAsStateWithLifecycle()
    val mixes by libraryViewModel.youTubeMixForYou.collectAsStateWithLifecycle()
    val reloadDestination by sharedViewModel.reloadDestination.collectAsStateWithLifecycle()
    val mood by viewModel.exploreMoodItem.collectAsStateWithLifecycle()
    val chart by viewModel.chart.collectAsStateWithLifecycle()
    val region by viewModel.regionCodeChart.collectAsStateWithLifecycle()
    val chartLoading by viewModel.loadingChart.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val isScrollingUp by listState.isScrollingUp()
    var createPlaylist by rememberSaveable { mutableStateOf(false) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    val sortedPlaylists = remember(playlists) { playlists.sortedByDescending { it.inLibrary } }
    val colors = MaterialTheme.colorScheme
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val refresh: () -> Unit = {
        viewModel.refreshRecentSongs()
        if (params == HOME_PARAMS_MIX_FOR_YOU) libraryViewModel.getYouTubeMixedForYou()
        else viewModel.getHomeItemList(params)
    }
    LaunchedEffect(nowPlaying) { viewModel.refreshRecentSongs() }
    LaunchedEffect(params) {
        listState.scrollToItem(0)
        if (params == HOME_PARAMS_MIX_FOR_YOU && mixes.data.isNullOrEmpty()) libraryViewModel.getYouTubeMixedForYou()
    }
    LaunchedEffect(isScrollingUp) { onScrolling(isScrollingUp) }
    LaunchedEffect(reloadDestination) {
        if (reloadDestination == HomeDestination::class) {
            if (listState.firstVisibleItemIndex > 0) listState.animateScrollToItem(0) else refresh()
            sharedViewModel.reloadDestinationDone()
        }
    }
    // Observe current pagination state, rather than capturing its first composition in remember.
    LaunchedEffect(listState, homeListState, continuation, params) {
        if (params != HOME_PARAMS_MIX_FOR_YOU && homeListState == ListState.IDLE && continuation != null) {
            snapshotFlow {
                val info = listState.layoutInfo
                info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 3
            }.collect { nearEnd -> if (nearEnd) viewModel.getContinueHomeItem(continuation) }
        }
    }
    if (createPlaylist) {
        AlertDialog(
            onDismissRequest = { createPlaylist = false },
            title = { Text("Create playlist", color = colors.onSurface) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = playlistName, onValueChange = { playlistName = it },
                    label = { Text("Playlist name") }, singleLine = true,
                )
            },
            confirmButton = {
                TextButton(enabled = playlistName.isNotBlank(), onClick = {
                    libraryViewModel.createPlaylist(playlistName.trim())
                    playlistName = ""
                    createPlaylist = false
                }) { Text("Create", color = if (playlistName.isNotBlank()) colors.primary else colors.onSurfaceVariant) }
            },
            dismissButton = { TextButton(onClick = { createPlaylist = false }) { Text("Cancel", color = colors.onSurface) } },
        )
    }
    Column(Modifier.fillMaxWidth().background(colors.background)) {
        HomeTopAppBar(navController, accountInfo)
        PullToRefreshBox(
            isRefreshing = if (params == HOME_PARAMS_MIX_FOR_YOU) mixes is echo.music.iad1tya.domain.utils.LocalResource.Loading else loading,
            onRefresh = refresh,
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = innerPadding.calculateLeftPadding(layoutDirection),
                    end = innerPadding.calculateRightPadding(layoutDirection),
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item(key = "greeting") { HomeGreeting() }
                item(key = "filters") {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOfHomeChip.forEach { id ->
                            val filter = when (id) {
                                Res.string.mix_for_you -> HOME_PARAMS_MIX_FOR_YOU
                                Res.string.relax -> HOME_PARAMS_RELAX
                                Res.string.sleep -> HOME_PARAMS_SLEEP
                                Res.string.energize -> HOME_PARAMS_ENERGIZE
                                Res.string.sad -> HOME_PARAMS_SAD
                                Res.string.romance -> HOME_PARAMS_ROMANCE
                                Res.string.feel_good -> HOME_PARAMS_FEEL_GOOD
                                Res.string.workout -> HOME_PARAMS_WORKOUT
                                Res.string.party -> HOME_PARAMS_PARTY
                                Res.string.commute -> HOME_PARAMS_COMMUTE
                                Res.string.focus -> HOME_PARAMS_FOCUS
                                else -> null
                            }
                            Chip(isAnimated = false, isSelected = params == filter, text = stringResource(id)) {
                                viewModel.setParams(filter)
                            }
                        }
                    }
                }
                if (params == null) {
                    if (sortedPlaylists.isNotEmpty()) {
                        item(key = "quick_access") {
                            HomeQuickAccess(sortedPlaylists.take(6)) { playlist ->
                                navController.navigate(echo.music.iad1tya.ui.navigation.destination.list.LocalPlaylistDestination(playlist.id))
                            }
                        }
                    }
                    if (recentSongs.isNotEmpty()) {
                        item(key = "recent") {
                            HomeRecentShelf(recentSongs, onSeeAll = { navController.navigate(RecentlySongsDestination) }) { song ->
                                val track = song.toTrack()
                                viewModel.setQueueData(QueueData.Data(
                                    listTracks = arrayListOf(track), firstPlayedTrack = track,
                                    playlistId = "RDAMVM${song.videoId}", playlistName = song.title,
                                    playlistType = PlaylistType.RADIO, continuation = null,
                                ))
                                viewModel.loadMediaItem(track, Config.SONG_CLICK)
                            }
                        }
                    }
                    item(key = "your_playlists") {
                        HomePlaylistShelf(sortedPlaylists, onCreate = { createPlaylist = true }) {
                            navController.navigate(echo.music.iad1tya.ui.navigation.destination.list.LocalPlaylistDestination(it.id))
                        }
                    }
                    item(key = "import") {
                        echo.music.iad1tya.ui.screen.library.PlaylistImportEntryCard(
                            onClick = { navController.navigate(echo.music.iad1tya.ui.navigation.destination.library.SpotifyPlaylistImportDestination) },
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
                if (params == HOME_PARAMS_MIX_FOR_YOU) {
                    if (!mixes.data.isNullOrEmpty()) {
                        item(key = "mixes") {
                            Column(Modifier.padding(horizontal = 20.dp)) {
                                HomeSectionHeading(stringResource(Res.string.mix_for_you))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(mixes.data.orEmpty()) { playlist ->
                                        HomeItemContentPlaylist(onClick = { navController.navigate(PlaylistDestination(playlist.browseId)) }, data = playlist)
                                    }
                                }
                            }
                        }
                    } else if (mixes !is echo.music.iad1tya.domain.utils.LocalResource.Loading) {
                        item { HomeEmptyDiscovery(stringResource(Res.string.no_mixes_found), refresh) }
                    }
                } else {
                    itemsIndexed(homeData, key = { index, item -> "discovery:$index:${item.title}" }) { _, item ->
                        HomeDiscoveryShelf(item, navController, viewModel)
                    }
                    if (loading || homeListState == ListState.PAGINATING) {
                        item(key = "loading") {
                            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text("Loading music", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                            }
                        }
                    } else if (homeData.isEmpty()) {
                        item(key = "offline") { HomeEmptyDiscovery("Discovery is unavailable. Your library is still here.", refresh) }
                    }
                    if (homeListState == ListState.PAGINATION_EXHAUST && !loading) {
                        itemsIndexed(newRelease, key = { index, item -> "release:$index:${item.title}" }) { _, item ->
                            HomeDiscoveryShelf(item, navController, viewModel)
                        }
                        mood?.let { data -> item(key = "mood") {
                            Box(Modifier.padding(horizontal = 20.dp)) { MoodMomentAndGenre(data, navController) }
                        } }
                        chart?.let { data -> item(key = "charts") {
                            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                HomeSectionHeading(stringResource(Res.string.chart))
                                DropdownButton(
                                    items = CHART_SUPPORTED_COUNTRY.itemsData.toList(),
                                    defaultSelected = CHART_SUPPORTED_COUNTRY.itemsData.getOrNull(CHART_SUPPORTED_COUNTRY.items.indexOf(region))
                                        ?: CHART_SUPPORTED_COUNTRY.itemsData.first(),
                                ) { selected -> viewModel.exploreChart(CHART_SUPPORTED_COUNTRY.items[CHART_SUPPORTED_COUNTRY.itemsData.indexOf(selected)]) }
                                if (chartLoading) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                                ChartData(data, navController)
                            }
                        } }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopAppBar(navController: NavController, accountInfo: Pair<String?, String?>?) {
    TopAppBar(
        windowInsets =
            TopAppBarDefaults.windowInsets.exclude(
                TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Start),
            ),
        navigationIcon = {
            androidx.compose.material3.IconButton(
                onClick = { navController.navigate(SettingsDestination) }
            ) {
                if (!accountInfo?.second.isNullOrEmpty()) {
                    coil3.compose.AsyncImage(
                        model =
                            coil3.request.ImageRequest
                                .Builder(coil3.compose.LocalPlatformContext.current)
                                .data(accountInfo?.second)
                                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                                .diskCacheKey(accountInfo?.second)
                                .crossfade(true)
                                .build(),
                        placeholder = echo.music.iad1tya.ui.component.rememberHolderPainter(),
                        error = echo.music.iad1tya.ui.component.rememberHolderPainter(),
                        contentDescription = "Settings",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(30.dp)
                                .clip(
                                    androidx.compose.foundation.shape.CircleShape,
                                ),
                    )
                } else {
                    androidx.compose.material3.Icon(
                        imageVector = echoIcons.Menu,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        },
        title = {
            Text(
                text = stringResource(Res.string.app_name),
                style = typo().titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        },
        actions = {
            androidx.compose.material3.IconButton(onClick = { navController.navigate(RecentlySongsDestination) }) {
                androidx.compose.material3.Icon(echoIcons.History, contentDescription = "Listening history", tint = MaterialTheme.colorScheme.onBackground)
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
            ),
    )
}

@Composable
fun AccountLayout(
    accountName: String,
    url: String,
) {
    Column {
        Text(
            text = stringResource(Res.string.welcome_back),
            style = typo().bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 3.dp),
        )
        if (accountName.isNotEmpty() && url.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
            ) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalPlatformContext.current)
                            .data(url)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .diskCacheKey(url)
                            .crossfade(true)
                            .build(),
                    placeholder = rememberHolderPainter(),
                    error = rememberHolderPainter(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(
                                CircleShape,
                            ),
                )
                Text(
                    text = accountName,
                    style = typo().headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier =
                        Modifier
                            .padding(start = 8.dp),
                )
            }
        }
    }
}

@ExperimentalFoundationApi
@Composable
fun QuickPicks(
    homeItem: HomeItem,
    navController: NavController,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val lazyListState = rememberLazyGridState()
    val snapperFlingBehavior = rememberSnapFlingBehavior(SnapLayoutInfoProvider(lazyGridState = lazyListState, snapPosition = SnapPosition.Start))
    val density = LocalDensity.current
    var widthDp by remember {
        mutableStateOf(0.dp)
    }
    var bottomSheetShow by remember { mutableStateOf(false) }
    var track by remember { mutableStateOf<Track?>(null) }

    if (bottomSheetShow) {
        NowPlayingBottomSheet(
            onDismiss = { bottomSheetShow = false },
            song = track?.toSongEntity(),
            navController = navController,
        )
    }

    Column(
        Modifier
            .padding(vertical = 8.dp)
            .onGloballyPositioned { coordinates ->
                with(density) {
                    widthDp = (coordinates.size.width).toDp()
                }
            },
    ) {
        Text(
            text = stringResource(Res.string.let_s_start_with_a_radio),
            style = typo().bodySmall,
        )
        Text(
            text = stringResource(Res.string.quick_picks),
            style = typo().headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
        )
        LazyHorizontalGrid(
            rows = GridCells.Fixed(4),
            modifier = Modifier.height(256.dp),
            state = lazyListState,
            flingBehavior = snapperFlingBehavior,
        ) {
            items(homeItem.contents, key = { it.hashCode() }) {
                if (it != null) {
                    QuickPicksItem(
                        onClick = {
                            val firstQueue: Track = it.toTrack()
                            viewModel.setQueueData(
                                QueueData.Data(
                                    listTracks = arrayListOf(firstQueue),
                                    firstPlayedTrack = firstQueue,
                                    playlistId = "RDAMVM${it.videoId}",
                                    playlistName = "\"${it.title}\" Radio",
                                    playlistType = PlaylistType.RADIO,
                                    continuation = null,
                                ),
                            )
                            viewModel.loadMediaItem(
                                firstQueue,
                                type = Config.SONG_CLICK,
                            )
                        },
                        onLongClick = {
                            track = it.toTrack()
                            bottomSheetShow = true
                        },
                        data = it,
                        widthDp = widthDp,
                    )
                }
            }
        }
    }
}

@Composable
fun MoodMomentAndGenre(
    mood: Mood,
    navController: NavController,
) {
    Column(
        Modifier
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = stringResource(Res.string.let_s_pick_a_playlist_for_you),
            style = typo().bodyMedium,
        )
        // One block per section YouTube returned, headed by ITS OWN title. Hard-coding
        // "Moods & moment" / "Genre" here (and reading mood.moodsMoments / mood.genres by
        // index) mislabelled every row as soon as a signed-in account got an extra
        // "For you" section, and hid the real Genres section altogether.
        mood.sections.forEach { section ->
            val gridState = rememberLazyGridState()
            val flingBehavior = rememberSnapFlingBehavior(SnapLayoutInfoProvider(lazyGridState = gridState))
            Text(
                text = section.title,
                style = typo().headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
            )
            LazyHorizontalGrid(
                rows = GridCells.Fixed(3),
                modifier = Modifier.height(210.dp),
                state = gridState,
                flingBehavior = flingBehavior,
            ) {
                items(section.items, key = { it.params }) { item ->

                    MoodMomentAndGenreHomeItem(
                        title = item.title,
                        stripeColor = item.stripeColor,
                    ) {
                        navController.navigate(
                            MoodDestination(
                                item.params,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChartTitle() {
    Column {
        Text(
            text = stringResource(Res.string.what_is_best_choice_today),
            style = typo().bodyMedium,
        )
        Text(
            text = stringResource(Res.string.chart),
            style = typo().headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
        )
    }
}

@Composable
fun ChartData(
    chart: Chart,
    navController: NavController,
) {
    var gridWidthDp by remember {
        mutableStateOf(0.dp)
    }
    val density = LocalDensity.current

    val lazyListState2 = rememberLazyGridState()
    val snapperFlingBehavior2 = rememberSnapFlingBehavior(SnapLayoutInfoProvider(lazyGridState = lazyListState2))

    Column(
        Modifier.onGloballyPositioned { coordinates ->
            with(density) {
                gridWidthDp = (coordinates.size.width).toDp()
            }
        },
    ) {
        chart.listChartItem.forEach { item ->
            Text(
                text = item.title,
                style = typo().headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
            )
            val lazyListState = rememberLazyListState()
            val snapperFlingBehavior = rememberSnapFlingBehavior(SnapLayoutInfoProvider(lazyListState = lazyListState))
            LazyRow(flingBehavior = snapperFlingBehavior) {
                items(item.playlists.size, key = { index ->
                    val data = item.playlists[index]
                    data.id + data.title + index
                }) {
                    HomeItemContentPlaylist(
                        onClick = {
                            navController.navigate(
                                PlaylistDestination(
                                    playlistId = item.playlists[it].id,
                                    isYourYouTubePlaylist = false,
                                ),
                            )
                        },
                        data = item.playlists[it],
                    )
                }
            }
        }
        Text(
            text = stringResource(Res.string.top_artists),
            style = typo().headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
        )
        LazyHorizontalGrid(
            rows = GridCells.Fixed(3),
            modifier = Modifier.height(240.dp),
            state = lazyListState2,
            flingBehavior = snapperFlingBehavior2,
        ) {
            items(chart.artists.itemArtists.size, key = { index ->
                val item = chart.artists.itemArtists[index]
                item.title + item.browseId + index
            }) {
                val data = chart.artists.itemArtists[it]
                ItemArtistChart(
                    onClick = {
                        navController.navigate(
                            ArtistDestination(
                                channelId = data.browseId,
                            ),
                        )
                    },
                    data = data,
                    widthDp = gridWidthDp,
                )
            }
        }
    }
}
