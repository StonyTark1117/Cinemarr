package stonytark.cinemarr.gametest;

import stonytark.cinemarr.core.server.ChunkTransferPolicy;
import stonytark.cinemarr.core.server.SlidingWindowRateLimiter;
import stonytark.cinemarr.core.server.RetryGate;
import stonytark.cinemarr.core.client.VideoSegmentAssembler;
import stonytark.cinemarr.core.network.Hashing;
import stonytark.cinemarr.core.server.TelevisionPolicy;
import stonytark.cinemarr.core.server.WatchPartyRegistry;
import stonytark.cinemarr.core.video.RenditionPolicy;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import stonytark.cinemarr.registry.CinemarrBlocks;
import stonytark.cinemarr.registry.CinemarrItems;
import stonytark.cinemarr.screen.CinemarrWorldScreens;
import stonytark.cinemarr.screen.QuickTvBlock;
import stonytark.cinemarr.screen.ScreenPixelBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import stonytark.cinemarr.Cinemarr;
import stonytark.cinemarr.core.server.TelevisionLifecycle;
import java.util.UUID;
import java.util.List;

@GameTestHolder(Cinemarr.MODID)
@PrefixGameTestTemplate(false)
public final class CinemarrGameTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void serverLoadsVideoRateLimitAndRetryCode(GameTestHelper helper) {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(); UUID player = UUID.randomUUID();
        helper.assertTrue(limiter.allow(player, 1, 1_000) && !limiter.allow(player, 1, 1_100), "Server request rate limit was not enforced");
        RetryGate retry = new RetryGate(); retry.deferUntil(30_000);
        helper.assertTrue(!retry.ready(29_999) && retry.ready(30_000), "Plex retry gate ignored its deadline");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void serverTransportRequiresManifestOrderAndAcknowledgement(GameTestHelper helper) {
        UUID session = UUID.randomUUID();
        ChunkTransferPolicy.State state = ChunkTransferPolicy.initial(session, 4, 1_000);
        helper.assertTrue(!ChunkTransferPolicy.acceptsRequest(state, session, 1, 0, 8, 40, 1_000), "Transfer accepted a pre-manifest chunk index");
        helper.assertTrue(ChunkTransferPolicy.acceptsRequest(state, session, 1, 4, 8, 40, 1_000), "Transfer rejected the manifest window");
        state = ChunkTransferPolicy.begin(session, 1, 4, 8, 1_000);
        helper.assertTrue(!ChunkTransferPolicy.acceptsRequest(state, session, 2, 12, 8, 40, 1_100), "Transfer advanced without an acknowledgement");
        helper.assertTrue(!ChunkTransferPolicy.withinPlaybackLead(17_001, 5_000, 12_000), "Transfer ran ahead of the authoritative playback window");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void videoRenditionHonorsNamedTargetAndServerCaps(GameTestHelper helper) {
        RenditionPolicy.Dimensions named = RenditionPolicy.chooseForScreen(16, 9, 256, 144,
                3_840, 2_160, 3_840, 2_160);
        helper.assertTrue(named.width() == 256 && named.height() == 144,
                "144p Quick TV did not preserve its named rendition");
        RenditionPolicy.Dimensions capped = RenditionPolicy.choose(80, 45, 7_680, 4_320, 1_920, 1_080);
        helper.assertTrue(capped.width() == 1_920 && capped.height() == 1_080,
                "Source rendition exceeded the server video cap");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void videoSegmentAssemblyRejectsWrongGenerationAndVerifiesHash(GameTestHelper helper) {
        byte[] first = new byte[]{1,2,3}, second = new byte[]{4,5};
        byte[] complete = new byte[]{1,2,3,4,5}; String hash = Hashing.sha256(complete); UUID session = UUID.randomUUID();
        VideoSegmentAssembler assembler = new VideoSegmentAssembler(); assembler.begin(session, 7, 1, 2, 2, hash, 4_000, true);
        helper.assertTrue(assembler.accept(session, 6, 1, 2, 0, 2, hash, 4_000, true, first).isEmpty(),
                "Assembler accepted a stale playback generation");
        helper.assertTrue(assembler.accept(session, 7, 1, 2, 1, 2, hash, 4_000, true, second).isEmpty(),
                "Assembler completed before every chunk arrived");
        helper.assertTrue(assembler.accept(session, 7, 1, 2, 0, 2, hash, 4_000, true, first).isPresent(),
                "Assembler did not complete a valid hash-verified segment");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void watchPartySharesOneSessionAndReleasesCapacity(GameTestHelper helper) {
        WatchPartyRegistry registry = new WatchPartyRegistry(1); UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        WatchPartyRegistry.Session one = registry.tune("lounge", first);
        WatchPartyRegistry.Session two = registry.tune("lounge", second);
        helper.assertTrue(one.id().equals(two.id()) && two.televisions().size() == 2,
                "Two TVs did not share one named playback session");
        helper.assertTrue(registry.untune("lounge", first) && registry.untune("lounge", second)
                && registry.activeSessions() == 0, "Empty watch-party session did not release capacity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void televisionOwnershipLimitReleasesAfterTeardown(GameTestHelper helper) {
        TelevisionPolicy policy = new TelevisionPolicy(1); UUID owner = UUID.randomUUID();
        helper.assertTrue(policy.claim(owner) && !policy.claim(owner), "Owner exceeded the configured television limit");
        policy.release(owner);
        helper.assertTrue(policy.claim(owner) && policy.ownedBy(owner) == 1, "Teardown did not release owner capacity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void quickTvBuildsBoundedPrefabAndPersistsRendition(GameTestHelper helper) {
        BlockPos relative = new BlockPos(8, 1, 8);
        BlockPos controller = helper.absolutePos(relative);
        QuickTvBlock block = CinemarrBlocks.QUICK_TV_144P.get();
        var state = block.defaultBlockState().setValue(QuickTvBlock.FACING, Direction.NORTH);
        helper.setBlock(relative, state);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        block.setPlacedBy(helper.getLevel(), controller, state, player, ItemStack.EMPTY);
        CinemarrWorldScreens.Television television = CinemarrWorldScreens.get(helper.getLevel()).television(controller);
        helper.assertTrue(television != null, "144p Quick TV did not activate");
        helper.assertTrue(television.width() == 16 && television.height() == 9 && television.pixels().size() == 144,
                "144p Quick TV did not build its bounded 16:9 prefab");
        helper.assertTrue(television.renditionWidth() == 256 && television.renditionHeight() == 144,
                "144p Quick TV did not persist its named rendition target");
        for (Long packed : new java.util.ArrayList<>(television.pixels())) {
            helper.getLevel().destroyBlock(BlockPos.of(packed), false);
        }
        helper.getLevel().destroyBlock(controller, false);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void quickTvRollbackPreservesPreexistingPixels(GameTestHelper helper) {
        BlockPos relative = new BlockPos(8, 1, 8);
        BlockPos controller = helper.absolutePos(relative);
        BlockPos strayRelative = new BlockPos(16, 1, 7);
        BlockPos stray = helper.absolutePos(strayRelative);
        QuickTvBlock block = CinemarrBlocks.QUICK_TV_144P.get();
        var state = block.defaultBlockState().setValue(QuickTvBlock.FACING, Direction.NORTH);
        var pixel = CinemarrBlocks.screenPixel().defaultBlockState().setValue(ScreenPixelBlock.FACING, Direction.NORTH);
        helper.setBlock(strayRelative, pixel);
        helper.setBlock(relative, state);
        List<BlockPos> initiallyAir = new java.util.ArrayList<>();
        BlockPos anchor = controller.relative(Direction.NORTH).relative(Direction.EAST, -8);
        for (int y = 0; y < 9; y++) for (int x = 0; x < 16; x++) {
            BlockPos target = anchor.relative(Direction.EAST, x).above(y);
            if (helper.getLevel().getBlockState(target).isAir()) initiallyAir.add(target);
        }
        helper.assertTrue(!initiallyAir.isEmpty(), "Rollback test did not begin with any empty footprint blocks");
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        block.setPlacedBy(helper.getLevel(), controller, state, player, ItemStack.EMPTY);
        helper.assertTrue(CinemarrWorldScreens.get(helper.getLevel()).television(controller) == null,
                "Quick TV unexpectedly activated across an incomplete 17x9 mask");
        helper.assertTrue(helper.getLevel().getBlockState(stray).is(CinemarrBlocks.screenPixel()),
                "Quick TV rollback deleted a pre-existing screen pixel");
        for (BlockPos newlyPlaced : initiallyAir) helper.assertTrue(helper.getLevel().getBlockState(newlyPlaced).isAir(),
                "Quick TV rollback left a newly placed pixel behind: " + newlyPlaced + "=" + helper.getLevel().getBlockState(newlyPlaced));
        helper.getLevel().destroyBlock(stray, false);
        helper.getLevel().destroyBlock(controller, false);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void televisionBreakAndExplosionPathsReleaseRegistration(GameTestHelper helper) {
        BlockPos relative = new BlockPos(8, 1, 8);
        BlockPos controller = helper.absolutePos(relative);
        QuickTvBlock block = CinemarrBlocks.QUICK_TV_144P.get();
        var state = block.defaultBlockState().setValue(QuickTvBlock.FACING, Direction.NORTH);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        helper.setBlock(relative, state);
        block.setPlacedBy(helper.getLevel(), controller, state, player, ItemStack.EMPTY);
        CinemarrWorldScreens screens = CinemarrWorldScreens.get(helper.getLevel());
        CinemarrWorldScreens.Television first = screens.television(controller);
        helper.assertTrue(first != null && TelevisionLifecycle.count(player.getUUID()) == 1,
                "Quick TV did not consume one owner registration");

        BlockPos removedPixel = BlockPos.of(first.pixels().iterator().next());
        helper.getLevel().destroyBlock(removedPixel, false);
        helper.assertTrue(screens.television(controller) == null && TelevisionLifecycle.count(player.getUUID()) == 0,
                "Breaking a registered pixel did not immediately unregister its TV");
        helper.getLevel().setBlockAndUpdate(removedPixel, CinemarrBlocks.screenPixel().defaultBlockState()
                .setValue(ScreenPixelBlock.FACING, Direction.NORTH));
        CinemarrWorldScreens.Activation repaired = screens.activate(controller, player.getUUID());
        helper.assertTrue(repaired.success(), "Repairing a pixel could not be manually reactivated: " + repaired.message());
        helper.getLevel().destroyBlock(controller, false);
        helper.assertTrue(screens.television(controller) == null && TelevisionLifecycle.count(player.getUUID()) == 0,
                "Breaking a Quick TV controller did not release its registration");

        helper.setBlock(relative, state);
        block.setPlacedBy(helper.getLevel(), controller, state, player, ItemStack.EMPTY);
        helper.assertTrue(screens.television(controller) != null, "Quick TV did not rebuild for explosion coverage");
        helper.getLevel().explode(null, controller.getX() + 0.5, controller.getY() + 0.5,
                controller.getZ() + 0.5, 2.0F, Level.ExplosionInteraction.BLOCK);
        helper.assertTrue(screens.television(controller) == null && TelevisionLifecycle.count(player.getUUID()) == 0,
                "Explosion removal did not immediately release the TV registration");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void optionalTvControlsRegisterAndReceiveRedstone(GameTestHelper helper) {
        BlockPos receiver = new BlockPos(2, 1, 2);
        helper.setBlock(receiver, CinemarrBlocks.REDSTONE_RECEIVER.get().defaultBlockState());
        helper.setBlock(receiver.offset(1, 0, 0), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.assertTrue(helper.getLevel().hasNeighborSignal(helper.absolutePos(receiver)),
                "TV Redstone Receiver did not observe adjacent power");
        helper.assertTrue("cinemarr:tv_remote".equals(BuiltInRegistries.ITEM.getKey(CinemarrItems.TV_REMOTE.get()).toString()),
                "TV Remote was not registered under the expected item ID");
        helper.succeed();
    }
    private CinemarrGameTests() {}
}
