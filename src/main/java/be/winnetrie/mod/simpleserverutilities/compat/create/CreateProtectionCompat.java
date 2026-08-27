package be.winnetrie.mod.simpleserverutilities.compat.create;

import be.winnetrie.mod.simpleserverutilities.SimpleServerUtilities;
import be.winnetrie.mod.simpleserverutilities.protection.ProtectionBoundary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Optional Create bridge for SSU protection.
 *
 * <p>This class deliberately has no compile-time dependency on Create. Pseudo
 * mixins pass Create objects in as {@link Object}; the small amount of context
 * SSU needs is read reflectively and cached per runtime class. This keeps SSU
 * loadable when Create is not installed and leaves the actual policy in
 * {@link ProtectionBoundary}.</p>
 */
public final class CreateProtectionCompat {

    private static final String BLOCKED_KEY = "SSUProtectedBoundary";
    private static final String BLOCKED_X = "SSUProtectedBoundaryX";
    private static final String BLOCKED_Y = "SSUProtectedBoundaryY";
    private static final String BLOCKED_Z = "SSUProtectedBoundaryZ";

    private static final Map<Class<?>, Field> BREAKING_POS_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field> CONTRAPTION_ANCHOR_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field> CONTRAPTION_BLOCK_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field> CONTRAPTION_STALLED_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field> CONTRAPTION_ENTITY_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field> MOVED_CONTRAPTION_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, MovementContextFields> MOVEMENT_CONTEXT_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field> BLOCK_ENTITY_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field> TARGET_CAPABILITY_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field> FLUID_ROOT_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field> OWNER_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Field> ENTITY_CONTRAPTION_FIELDS = new ConcurrentHashMap<>();

    // Reflection is unavoidable because Create remains an optional dependency,
    // but method discovery must never sit on a per-tick hot path. Cache both
    // successful lookups and misses per runtime class.
    private static final Map<Class<?>, Method> CAPABILITY_GET_TARGET_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> BLOCK_FACE_CONNECTED_POS_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> TO_GLOBAL_VECTOR_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> MAKE_STRUCTURE_TRANSFORM_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> STRUCTURE_TRANSFORM_APPLY_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> CONTRAPTION_GETTER_METHODS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Boolean> TRAIN_CARRIAGE_CLASS_CACHE = new ConcurrentHashMap<>();

    private static volatile Object fluidBlockingSpaceType;
    private static volatile boolean fluidBlockingSpaceTypeResolved;
    private static volatile Object rollerPaveFailResult;
    private static volatile boolean rollerPaveFailResolved;

    /**
     * Synchronous source stack used while Create performs secondary block
     * destruction (notably Mechanical Saw tree-felling). This lets the common
     * BlockHelper hook validate every queued log/leaf independently.
     */
    private static final ThreadLocal<ArrayDeque<AutomationBreakOrigin>> AUTOMATION_BREAK_ORIGINS =
            ThreadLocal.withInitial(ArrayDeque::new);

    private static final Field MISSING_FIELD;
    private static final Method MISSING_METHOD;

    static {
        try {
            MISSING_FIELD = MissingFieldSentinel.class.getDeclaredField("missing");
            MISSING_METHOD = MissingMethodSentinel.class.getDeclaredMethod("missing");
        } catch (NoSuchFieldException | NoSuchMethodException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private CreateProtectionCompat() {
    }


    /**
     * Checks whether a Create contraption being assembled may capture the
     * candidate block. The contraption anchor is the source side of the
     * operation; capturing a block across an SSU protection boundary is
     * rejected before Create adds it to the moving structure.
     */
    public static boolean canContraptionCapture(Object contraption, Level level, BlockPos candidate) {
        if (contraption == null || level == null || level.isClientSide || candidate == null) {
            return true;
        }

        Field field = CONTRAPTION_ANCHOR_FIELDS.computeIfAbsent(contraption.getClass(),
                type -> findField(type, "anchor"));
        if (field == MISSING_FIELD) {
            return true;
        }

        try {
            Object value = field.get(contraption);
            if (!(value instanceof BlockPos anchor)) {
                return true;
            }
            return ProtectionBoundary.canCross(level, anchor, candidate);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return true;
        }
    }

    /**
     * Prevents a stationary Create Deployer from interacting across an SSU
     * protection boundary. Current Create deployers act two blocks in front of
     * their block position.
     */
    public static boolean canStationaryDeployerActivate(Object deployer) {
        if (!(deployer instanceof BlockEntity blockEntity)) {
            return true;
        }

        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide) {
            return true;
        }

        Direction facing = findFacing(blockEntity.getBlockState());
        if (facing == null) {
            return true;
        }

        BlockPos source = blockEntity.getBlockPos();
        BlockPos reach = source.relative(facing);
        BlockPos target = source.relative(facing, 2);
        UUID owner = readUuidField(deployer, OWNER_FIELDS, "owner");
        return ProtectionBoundary.canCross(level, source, reach, owner)
                && ProtectionBoundary.canCross(level, source, target, owner);
    }

    /**
     * Starts a synchronous Create block-destruction context for a stationary
     * saw. TreeCutter performs its extra log/leaf destruction inside
     * SawBlockEntity#onBlockBroken, so a ThreadLocal is safe and avoids any
     * hard Create dependency.
     */
    public static void beginStationarySawCascade(Object saw) {
        if (!(saw instanceof BlockEntity blockEntity)) {
            pushAutomationBreakOrigin(null, null, null);
            return;
        }
        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide) {
            pushAutomationBreakOrigin(null, null, null);
            return;
        }
        pushAutomationBreakOrigin(level, blockEntity.getBlockPos(), blockEntity.getBlockPos());
    }

    /** Starts the same protection context for a saw mounted on a contraption. */
    public static void beginMovingSawCascade(Object context, BlockPos brokenPos) {
        MovementContextView view = readMovementContext(context);
        if (view == null || view.level == null || view.level.isClientSide || brokenPos == null) {
            pushAutomationBreakOrigin(null, null, null);
            return;
        }
        BlockPos originSource = readContraptionAnchor(view.contraption);
        BlockPos localSource = deriveMovingActorLocalSource(view, brokenPos);
        if (originSource == null && localSource == null) {
            pushAutomationBreakOrigin(null, null, null);
            return;
        }
        pushAutomationBreakOrigin(view.level, originSource, localSource);
    }

    /** Ends the innermost synchronous Create block-destruction context. */
    public static void endSawCascade() {
        ArrayDeque<AutomationBreakOrigin> stack = AUTOMATION_BREAK_ORIGINS.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            AUTOMATION_BREAK_ORIGINS.remove();
        }
    }

    /**
     * Called from Create's shared BlockHelper before it destroys a queued block.
     * Outside an SSU-tagged automation context this is intentionally a no-op.
     */
    public static boolean canCreateAutomationDestroy(Level level, BlockPos target) {
        if (level == null || level.isClientSide || target == null) {
            return true;
        }
        ArrayDeque<AutomationBreakOrigin> stack = AUTOMATION_BREAK_ORIGINS.get();
        AutomationBreakOrigin origin = stack.peek();
        if (origin == null || origin.level() == null || origin.level() != level) {
            return true;
        }
        if (origin.originSource() != null && !ProtectionBoundary.canCross(level, origin.originSource(), target)) {
            return false;
        }
        return origin.localSource() == null || ProtectionBoundary.canCross(level, origin.localSource(), target);
    }

    /**
     * Blocks a Portable Storage Interface connection when the stationary
     * interface and the contraption originate in different SSU protection
     * areas. Because both item and fluid PSIs share the same base class, this
     * protects both transfer types at the connection point.
     */
    public static boolean canPortableInterfaceConnect(Object stationaryInterface, Object contraption) {
        if (!(stationaryInterface instanceof BlockEntity blockEntity)) {
            return true;
        }
        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide) {
            return true;
        }
        BlockPos source = readContraptionAnchor(contraption);
        if (source == null) {
            return true;
        }
        return ProtectionBoundary.canCross(level, source, blockEntity.getBlockPos());
    }

    /**
     * Generic guard for Create behaviours that discover an adjacent capability
     * (inventory manipulation used by funnels, arms and similar logistics).
     * The capability is discarded when its owner and target cross a protection
     * boundary, so Create cannot keep using a previously discovered handler.
     */
    public static boolean canCapabilityBehaviourConnect(Object behaviour) {
        if (behaviour == null) {
            return true;
        }

        Field blockEntityField = BLOCK_ENTITY_FIELDS.computeIfAbsent(behaviour.getClass(),
                type -> findField(type, "blockEntity"));
        if (blockEntityField == MISSING_FIELD) {
            return true;
        }

        try {
            Object value = blockEntityField.get(behaviour);
            if (!(value instanceof BlockEntity blockEntity)) {
                return true;
            }
            Level level = blockEntity.getLevel();
            if (level == null || level.isClientSide) {
                return true;
            }

            Method getTarget = cachedMethod(
                    CAPABILITY_GET_TARGET_METHODS, behaviour.getClass(), "getTarget", 0);
            if (getTarget == MISSING_METHOD) {
                return true;
            }
            Object blockFace = getTarget.invoke(behaviour);
            if (blockFace == null) {
                return true;
            }
            Method getConnectedPos = cachedMethod(
                    BLOCK_FACE_CONNECTED_POS_METHODS, blockFace.getClass(), "getConnectedPos", 0);
            if (getConnectedPos == MISSING_METHOD) {
                return true;
            }
            Object targetValue = getConnectedPos.invoke(blockFace);
            if (!(targetValue instanceof BlockPos target)) {
                return true;
            }

            boolean allowed = ProtectionBoundary.canCross(level, blockEntity.getBlockPos(), target);
            if (!allowed) {
                clearTargetCapability(behaviour);
            }
            return allowed;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return true;
        }
    }


    /**
     * Guards Create's hose-pulley fluid search/fill positions. Both the owning
     * machine position and the current hose/root position must belong to the
     * same SSU protection area as the candidate fluid position.
     */
    public static boolean canFluidManipulationAffect(Object behaviour, Level level, BlockPos target) {
        if (behaviour == null || level == null || level.isClientSide || target == null) {
            return true;
        }

        Field blockEntityField = BLOCK_ENTITY_FIELDS.computeIfAbsent(behaviour.getClass(),
                type -> findField(type, "blockEntity"));
        if (blockEntityField == MISSING_FIELD) {
            return true;
        }

        try {
            Object value = blockEntityField.get(behaviour);
            if (!(value instanceof BlockEntity blockEntity)) {
                return true;
            }

            BlockPos owner = blockEntity.getBlockPos();
            if (!ProtectionBoundary.canCross(level, owner, target)) {
                return false;
            }

            Field rootField = FLUID_ROOT_FIELDS.computeIfAbsent(behaviour.getClass(),
                    type -> findField(type, "rootPos"));
            if (rootField == MISSING_FIELD) {
                return true;
            }
            Object rootValue = rootField.get(behaviour);
            return !(rootValue instanceof BlockPos root) || ProtectionBoundary.canCross(level, root, target);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return true;
        }
    }

    /** Returns Create's FluidFillingBehaviour.SpaceType.BLOCKING without a hard Create dependency. */
    public static Object createFluidBlockingSpaceType() {
        if (fluidBlockingSpaceTypeResolved) {
            return fluidBlockingSpaceType;
        }
        synchronized (CreateProtectionCompat.class) {
            if (fluidBlockingSpaceTypeResolved) {
                return fluidBlockingSpaceType;
            }
            try {
                Class<?> type = Class.forName(
                        "com.simibubi.create.content.fluids.transfer.FluidFillingBehaviour$SpaceType",
                        false,
                        CreateProtectionCompat.class.getClassLoader());
                if (type.isEnum()) {
                    for (Object constant : type.getEnumConstants()) {
                        if (constant instanceof Enum<?> enumValue && "BLOCKING".equals(enumValue.name())) {
                            fluidBlockingSpaceType = constant;
                            break;
                        }
                    }
                }
            } catch (ClassNotFoundException | LinkageError | RuntimeException ignored) {
                // Optional Create compat: absence/version mismatch must not break SSU startup.
            }
            fluidBlockingSpaceTypeResolved = true;
            return fluidBlockingSpaceType;
        }
    }

    /** Returns RollerMovementBehaviour.PaveResult.FAIL without a hard Create dependency. */
    public static Object createRollerPaveFailResult() {
        if (rollerPaveFailResolved) {
            return rollerPaveFailResult;
        }
        synchronized (CreateProtectionCompat.class) {
            if (rollerPaveFailResolved) {
                return rollerPaveFailResult;
            }
            try {
                Class<?> type = Class.forName(
                        "com.simibubi.create.content.contraptions.actors.roller.RollerMovementBehaviour$PaveResult",
                        false,
                        CreateProtectionCompat.class.getClassLoader());
                if (type.isEnum()) {
                    for (Object constant : type.getEnumConstants()) {
                        if (constant instanceof Enum<?> enumValue && "FAIL".equals(enumValue.name())) {
                            rollerPaveFailResult = constant;
                            break;
                        }
                    }
                }
            } catch (ClassNotFoundException | LinkageError | RuntimeException ignored) {
                // Optional Create compat: absence/version mismatch must not break SSU startup.
            }
            rollerPaveFailResolved = true;
            return rollerPaveFailResult;
        }
    }

    /**
     * Checks a stationary Create block-breaking kinetic block entity.
     * Current Create drills and saws derive from BlockBreakingKineticBlockEntity.
     */
    public static boolean canStationaryBreakerAffect(Object breaker) {
        if (!(breaker instanceof BlockEntity blockEntity)) {
            return true;
        }

        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide) {
            return true;
        }

        BlockPos target = readBreakingPos(breaker);
        if (target == null) {
            return true;
        }

        return ProtectionBoundary.canCross(level, blockEntity.getBlockPos(), target);
    }

    /**
     * Called before a moving Create block-breaker starts working on a new block.
     * Returns {@code true} when Create may continue with its normal logic.
     */
    public static boolean canMovingBreakerVisit(Object context, BlockPos target) {
        MovementContextView view = readMovementContext(context);
        if (view == null || view.level == null || view.level.isClientSide) {
            return true;
        }

        if (movingActorBoundaryAllowed(view, target)) {
            clearBoundaryMarker(view);
            return true;
        }

        // A minecart/train-mounted contraption is transport. Hard-stalling a
        // protected actor on it can permanently trap the vehicle at the
        // boundary because Create zeroes the cart velocity while stalled. For
        // transport, cancel only the actor action; the cart itself may retreat
        // or pass through. Controller-driven contraptions still stall normally.
        if (isTransportContraption(view.contraption)) {
            if (view.data.getBoolean(BLOCKED_KEY)) {
                setStall(view, false);
            }
            clearBoundaryMarker(view);
            return false;
        }

        markBoundaryBlocked(view, target);
        setStall(view, true);
        return false;
    }


    /**
     * Generic moving-actor check for Create behaviours that directly modify a
     * visited block without using the shared block-breaker implementation.
     */
    public static boolean canMovingActorAffect(Object context, BlockPos target) {
        MovementContextView view = readMovementContext(context);
        if (view == null || view.level == null || view.level.isClientSide || target == null) {
            return true;
        }

        return movingActorBoundaryAllowed(view, target);
    }

    /**
     * Keeps a Create contraption stalled while a previously-blocked breaker is
     * still trying to work across a protected boundary. If the protection
     * relationship changes (for example the claim is removed), the marker is
     * cleared and Create may resume normally.
     */
    public static boolean keepMovingBreakerStalled(Object context) {
        MovementContextView view = readMovementContext(context);
        if (view == null || view.level == null || view.level.isClientSide || view.data == null) {
            return false;
        }
        if (!view.data.getBoolean(BLOCKED_KEY)) {
            return false;
        }

        // Release an SSU stall left by an older tick/build when this actor is
        // mounted on a transport contraption. The action itself will still be
        // denied by canMovingBreakerVisit(), but transport must never be
        // one-way trapped by protection.
        if (isTransportContraption(view.contraption)) {
            setStall(view, false);
            clearBoundaryMarker(view);
            return false;
        }

        BlockPos target = new BlockPos(
                view.data.getInt(BLOCKED_X),
                view.data.getInt(BLOCKED_Y),
                view.data.getInt(BLOCKED_Z));
        if (!movingActorBoundaryAllowed(view, target)) {
            setStall(view, true);
            return true;
        }

        clearBoundaryMarker(view);
        return false;
    }

    /** Clears SSU's temporary Create stall state when Create explicitly cancels/stops the actor. */
    public static void clearMovingBreakerBoundaryState(Object context) {
        MovementContextView view = readMovementContext(context);
        if (view != null) {
            clearBoundaryMarker(view);
        }
    }

    /**
     * Validates every block of a moving Create contraption against the SSU
     * protection area in which the contraption was assembled. This closes the
     * gap left by actor-only protection: a plain bearing/piston/gantry body can
     * no longer physically enter another claim or region just because it has
     * no drill/deployer attached.
     *
     * <p>Transport contraptions are deliberately excluded from body blocking.
     * This includes real Create train carriages and ordinary contraptions riding
     * minecarts/couplings. Their moving body is an entity rather than a world
     * mutation; hard-stalling it at a boundary can trap the underlying cart.
     * Mounted actors remain protected, and disassembly is still preflighted
     * before any blocks are placed back into the world.</p>
     */
    public static boolean enforceWholeContraptionBoundary(Object contraptionEntity) {
        if (!(contraptionEntity instanceof Entity entity)) {
            return true;
        }

        Level level = entity.level();
        if (level == null || level.isClientSide || isTransportContraptionEntity(contraptionEntity)) {
            return true;
        }

        Object contraption = readEntityContraption(contraptionEntity);
        BlockPos origin = readContraptionAnchor(contraption);
        if (contraption == null || origin == null) {
            return true;
        }

        // Performance fast path: the previous implementation transformed and
        // protection-checked every contraption block every tick. Most moving
        // structures are nowhere near a claim/region boundary, so first test
        // the entity envelope against chunk/region indexes. Only an envelope
        // that can actually touch another protection area falls through to the
        // exact block footprint scan below. Inflate by one block to remain
        // conservative around fractional motion and rotation rounding.
        AABB envelope = entity.getBoundingBox().inflate(1.0D);
        if (ProtectionBoundary.envelopeStaysInSameArea(
                level,
                origin,
                (int) Math.floor(envelope.minX),
                (int) Math.floor(envelope.minY),
                (int) Math.floor(envelope.minZ),
                (int) Math.ceil(envelope.maxX),
                (int) Math.ceil(envelope.maxY),
                (int) Math.ceil(envelope.maxZ))) {
            return true;
        }

        Map<?, ?> blocks = readContraptionBlocks(contraption);
        if (blocks == null || blocks.isEmpty()) {
            return true;
        }

        ProtectionBoundary.AreaSnapshot originArea = ProtectionBoundary.resolveArea(level, origin);
        Method toGlobalVector = cachedMethod(
                TO_GLOBAL_VECTOR_METHODS, contraptionEntity.getClass(), "toGlobalVector", 2);
        if (toGlobalVector == MISSING_METHOD) {
            return true;
        }

        try {
            for (Object key : blocks.keySet()) {
                if (!(key instanceof BlockPos localPos)) {
                    continue;
                }
                Object transformed = toGlobalVector.invoke(
                        contraptionEntity,
                        Vec3.atCenterOf(localPos),
                        1.0F);
                if (!(transformed instanceof Vec3 global)) {
                    continue;
                }
                BlockPos target = BlockPos.containing(global);
                if (!ProtectionBoundary.sameArea(level, originArea, target)) {
                    setContraptionStalled(contraption, true);
                    return false;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return true;
        }

        return true;
    }

    /**
     * Checks every final placement position before Create disassembles a moving
     * contraption back into world blocks. This prevents an otherwise-safe
     * moving entity from being materialised across a claim/region boundary.
     */
    public static boolean canContraptionDisassemble(Object contraptionEntity) {
        if (!(contraptionEntity instanceof Entity entity)) {
            return true;
        }

        Level level = entity.level();
        if (level == null || level.isClientSide) {
            return true;
        }

        Object contraption = readEntityContraption(contraptionEntity);
        BlockPos origin = readContraptionAnchor(contraption);
        if (contraption == null || origin == null) {
            return true;
        }

        // Disassembly is not a tick loop, but transforming tens of thousands of
        // positions in one server tick can still create a visible pause. When
        // the current contraption envelope is unquestionably inside the source
        // protection area, no exact placement scan is necessary.
        AABB envelope = entity.getBoundingBox().inflate(1.0D);
        if (ProtectionBoundary.envelopeStaysInSameArea(
                level,
                origin,
                (int) Math.floor(envelope.minX),
                (int) Math.floor(envelope.minY),
                (int) Math.floor(envelope.minZ),
                (int) Math.ceil(envelope.maxX),
                (int) Math.ceil(envelope.maxY),
                (int) Math.ceil(envelope.maxZ))) {
            return true;
        }

        Map<?, ?> blocks = readContraptionBlocks(contraption);
        if (blocks == null || blocks.isEmpty()) {
            return true;
        }

        ProtectionBoundary.AreaSnapshot originArea = ProtectionBoundary.resolveArea(level, origin);
        Method makeTransform = cachedMethod(
                MAKE_STRUCTURE_TRANSFORM_METHODS, contraptionEntity.getClass(), "makeStructureTransform", 0);
        if (makeTransform == MISSING_METHOD) {
            return true;
        }

        try {
            Object transform = makeTransform.invoke(contraptionEntity);
            if (transform == null) {
                return true;
            }

            BlockPos samplePos = firstBlockPosKey(blocks);
            if (samplePos == null) {
                return true;
            }
            Method apply = cachedCompatibleMethod(
                    STRUCTURE_TRANSFORM_APPLY_METHODS, transform.getClass(), "apply", samplePos);
            if (apply == MISSING_METHOD) {
                return true;
            }

            for (Object key : blocks.keySet()) {
                if (!(key instanceof BlockPos localPos)) {
                    continue;
                }
                Object targetValue = apply.invoke(transform, localPos);
                if (!(targetValue instanceof BlockPos target)) {
                    return true;
                }
                if (!ProtectionBoundary.sameArea(level, originArea, target)) {
                    setContraptionStalled(contraption, true);
                    return false;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return true;
        }

        return true;
    }

    /**
     * Controller-level preflight for Create controllers that clear their moved
     * entity reference after calling disassemble(). Cancelling here keeps the
     * controller and contraption attached instead of orphaning the entity.
     */
    public static boolean canControllerDisassemble(Object controller) {
        if (controller == null) {
            return true;
        }

        Field movedField = MOVED_CONTRAPTION_FIELDS.computeIfAbsent(controller.getClass(),
                type -> findField(type, "movedContraption"));
        if (movedField == MISSING_FIELD) {
            return true;
        }
        try {
            Object moved = movedField.get(controller);
            return moved == null || canContraptionDisassemble(moved);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return true;
        }
    }

    private static BlockPos readBreakingPos(Object breaker) {
        Field field = BREAKING_POS_FIELDS.computeIfAbsent(breaker.getClass(),
                type -> findField(type, "breakingPos"));
        if (field == MISSING_FIELD) {
            return null;
        }
        try {
            Object value = field.get(breaker);
            return value instanceof BlockPos pos ? pos : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static MovementContextView readMovementContext(Object context) {
        if (context == null) {
            return null;
        }
        MovementContextFields fields = MOVEMENT_CONTEXT_FIELDS.computeIfAbsent(
                context.getClass(), CreateProtectionCompat::resolveMovementContextFields);
        if (!fields.usable()) {
            return null;
        }

        try {
            Object worldValue = fields.world.get(context);
            Object stateValue = fields.state.get(context);
            Object dataValue = fields.data.get(context);
            Object blockEntityDataValue = fields.blockEntityData == MISSING_FIELD ? null : fields.blockEntityData.get(context);
            Object rotationValue = fields.rotation == MISSING_FIELD ? null : fields.rotation.get(context);
            Object relativeMotionValue = fields.relativeMotion == MISSING_FIELD ? null : fields.relativeMotion.get(context);
            Object contraptionValue = fields.contraption == MISSING_FIELD ? null : fields.contraption.get(context);

            if (!(worldValue instanceof Level level)
                    || !(stateValue instanceof BlockState state)
                    || !(dataValue instanceof CompoundTag data)) {
                return null;
            }

            return new MovementContextView(context, fields, level, state, data,
                    blockEntityDataValue instanceof CompoundTag blockEntityData ? blockEntityData : null,
                    rotationValue, relativeMotionValue, contraptionValue);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static MovementContextFields resolveMovementContextFields(Class<?> type) {
        Field world = findField(type, "world");
        Field state = findField(type, "state");
        Field data = findField(type, "data");
        Field blockEntityData = findField(type, "blockEntityData");
        Field stall = findField(type, "stall");
        Field rotation = findField(type, "rotation");
        Field relativeMotion = findField(type, "relativeMotion");
        Field contraption = findField(type, "contraption");
        return new MovementContextFields(world, state, data, blockEntityData, stall, rotation, relativeMotion, contraption);
    }

    private static void pushAutomationBreakOrigin(Level level, BlockPos originSource, BlockPos localSource) {
        AUTOMATION_BREAK_ORIGINS.get().push(new AutomationBreakOrigin(
                level,
                originSource == null ? null : originSource.immutable(),
                localSource == null ? null : localSource.immutable()));
    }

    private static void clearTargetCapability(Object behaviour) {
        Field targetCapabilityField = TARGET_CAPABILITY_FIELDS.computeIfAbsent(behaviour.getClass(),
                type -> findField(type, "targetCapability"));
        if (targetCapabilityField == MISSING_FIELD) {
            return;
        }
        try {
            targetCapabilityField.set(behaviour, null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Optional compat must not take down the server.
        }
    }

    private static Method cachedMethod(
            Map<Class<?>, Method> cache,
            Class<?> runtimeType,
            String name,
            int parameterCount
    ) {
        return cache.computeIfAbsent(runtimeType, type -> {
            Method method = findMethod(type, name, parameterCount);
            return method == null ? MISSING_METHOD : method;
        });
    }

    private static Method cachedCompatibleMethod(
            Map<Class<?>, Method> cache,
            Class<?> runtimeType,
            String name,
            Object argument
    ) {
        return cache.computeIfAbsent(runtimeType, type -> {
            Method method = findCompatibleMethod(type, name, argument);
            return method == null ? MISSING_METHOD : method;
        });
    }

    private static Method findMethod(Class<?> startType, String name, int parameterCount) {
        Class<?> type = startType;
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != parameterCount) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    return method;
                } catch (RuntimeException exception) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static BlockPos firstBlockPosKey(Map<?, ?> blocks) {
        if (blocks == null) {
            return null;
        }
        for (Object key : blocks.keySet()) {
            if (key instanceof BlockPos pos) {
                return pos;
            }
        }
        return null;
    }

    private static Method findCompatibleMethod(Class<?> startType, String name, Object argument) {
        Class<?> type = startType;
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameter = method.getParameterTypes()[0];
                if (argument != null && !parameter.isAssignableFrom(argument.getClass())) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    return method;
                } catch (RuntimeException exception) {
                    return null;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Field findField(Class<?> startType, String name) {
        Class<?> type = startType;
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (RuntimeException exception) {
                SimpleServerUtilities.LOGGER.debug("SSU Create compat could not access field {} on {}.", name,
                        startType.getName(), exception);
                return MISSING_FIELD;
            }
        }
        return MISSING_FIELD;
    }

    private static boolean movingActorBoundaryAllowed(MovementContextView view, BlockPos target) {
        if (target == null) {
            return true;
        }

        // Create itself persists the placing player's UUID for Deployers and
        // copies that NBT into MovementContext.blockEntityData. Use it only for
        // that known actor; other Create machinery remains ownerless unless its
        // own API provides an equally authoritative identity.
        UUID owner = readReliableMovementOwner(view);

        // Origin check: an actor assembled in wilderness (or another protected
        // area) never gains authority just because the contraption has moved
        // into the target area through empty space. A known owner may cross only
        // when SSU says that player can modify both sides.
        BlockPos originSource = readContraptionAnchor(view.contraption);
        if (originSource != null && !ProtectionBoundary.canCross(view.level, originSource, target, owner)) {
            return false;
        }

        // Local check: even when the original anchor belongs to the same area,
        // the actor itself may currently be approaching the target from across
        // a boundary. Require that immediate actor -> target transition too.
        BlockPos localSource = deriveMovingActorLocalSource(view, target);
        return localSource == null || ProtectionBoundary.canCross(view.level, localSource, target, owner);
    }

    private static UUID readReliableMovementOwner(MovementContextView view) {
        if (view == null || view.blockEntityData == null || !view.blockEntityData.contains("Owner")) {
            return null;
        }
        if (!isCreateDeployer(view.state)) {
            return null;
        }
        try {
            return view.blockEntityData.getUUID("Owner");
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean isCreateDeployer(BlockState state) {
        if (state == null || state.getBlock() == null) {
            return false;
        }
        String className = state.getBlock().getClass().getName();
        return "com.simibubi.create.content.kinetics.deployer.DeployerBlock".equals(className);
    }

    private static UUID readUuidField(Object instance, Map<Class<?>, Field> cache, String fieldName) {
        if (instance == null) {
            return null;
        }
        Field field = cache.computeIfAbsent(instance.getClass(), type -> findField(type, fieldName));
        if (field == MISSING_FIELD) {
            return null;
        }
        try {
            Object value = field.get(instance);
            return value instanceof UUID uuid ? uuid : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static BlockPos deriveMovingActorLocalSource(MovementContextView view, BlockPos target) {
        Direction facing = findFacing(view.state);
        if (facing != null) {
            Direction worldFacing = rotateDirection(facing, view.rotation);
            return target.relative(worldFacing.getOpposite());
        }

        // Fallback for a future/custom Create actor without a conventional
        // "facing" property. The movement direction is less exact than the
        // actor's facing, but still gives SSU a conservative adjacent source.
        if (view.relativeMotion instanceof Vec3 relativeMotion && relativeMotion.lengthSqr() > 1.0e-7) {
            Direction movement = nearestDirection(relativeMotion);
            return target.relative(movement.getOpposite());
        }

        return null;
    }

    private static BlockPos readContraptionAnchor(Object contraption) {
        if (contraption == null) {
            return null;
        }
        Field field = CONTRAPTION_ANCHOR_FIELDS.computeIfAbsent(contraption.getClass(),
                type -> findField(type, "anchor"));
        if (field == MISSING_FIELD) {
            return null;
        }
        try {
            Object value = field.get(contraption);
            return value instanceof BlockPos pos ? pos : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static Object readEntityContraption(Object contraptionEntity) {
        if (contraptionEntity == null) {
            return null;
        }
        Class<?> type = contraptionEntity.getClass();
        Method getter = cachedMethod(CONTRAPTION_GETTER_METHODS, type, "getContraption", 0);
        if (getter != MISSING_METHOD) {
            try {
                return getter.invoke(contraptionEntity);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Fall back to the protected field below.
            }
        }
        Field field = ENTITY_CONTRAPTION_FIELDS.computeIfAbsent(type,
                runtimeType -> findField(runtimeType, "contraption"));
        if (field == MISSING_FIELD) {
            return null;
        }
        try {
            return field.get(contraptionEntity);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> readContraptionBlocks(Object contraption) {
        if (contraption == null) {
            return null;
        }
        Field field = CONTRAPTION_BLOCK_FIELDS.computeIfAbsent(contraption.getClass(),
                type -> findField(type, "blocks"));
        if (field == MISSING_FIELD) {
            return null;
        }
        try {
            Object value = field.get(contraption);
            return value instanceof Map<?, ?> map ? map : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static void setContraptionStalled(Object contraption, boolean stalled) {
        if (contraption == null) {
            return;
        }
        Field field = CONTRAPTION_STALLED_FIELDS.computeIfAbsent(contraption.getClass(),
                type -> findField(type, "stalled"));
        if (field == MISSING_FIELD) {
            return;
        }
        try {
            field.setBoolean(contraption, stalled);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Optional Create compat must never take down the server.
        }
    }

    private static boolean isTransportContraption(Object contraption) {
        if (contraption == null) {
            return false;
        }
        Field field = CONTRAPTION_ENTITY_FIELDS.computeIfAbsent(contraption.getClass(),
                type -> findField(type, "entity"));
        if (field == MISSING_FIELD) {
            return false;
        }
        try {
            Object entity = field.get(contraption);
            return isTransportContraptionEntity(entity);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    /**
     * Transport contraptions are allowed to cross protection areas as entities.
     * Their actors and world-placement/disassembly remain protected separately.
     *
     * <p>In particular, an ordinary minecart contraption must not be hard-
     * stalled by the whole-body check. Create zeroes the riding minecart's
     * velocity while the contraption is stalled, so reasserting that stall each
     * tick creates a one-way trap at the boundary and prevents retreat.</p>
     */
    private static boolean isTransportContraptionEntity(Object contraptionEntity) {
        if (contraptionEntity == null) {
            return false;
        }
        if (isTrainCarriageContraptionEntity(contraptionEntity.getClass())) {
            return true;
        }
        if (!(contraptionEntity instanceof Entity entity)) {
            return false;
        }

        // Normal minecart contraptions ride the cart directly. Stabilized or
        // nested contraptions can ride another contraption first, so walk the
        // complete vehicle chain with a small safety guard.
        Entity vehicle = entity.getVehicle();
        int guard = 0;
        while (vehicle != null && guard++ < 16) {
            if (vehicle instanceof AbstractMinecart) {
                return true;
            }
            vehicle = vehicle.getVehicle();
        }
        return false;
    }

    private static boolean isTrainCarriageContraptionEntity(Class<?> startType) {
        if (startType == null) {
            return false;
        }
        return TRAIN_CARRIAGE_CLASS_CACHE.computeIfAbsent(startType, type -> {
            Class<?> cursor = type;
            while (cursor != null) {
                if ("com.simibubi.create.content.trains.entity.CarriageContraptionEntity".equals(cursor.getName())) {
                    return true;
                }
                cursor = cursor.getSuperclass();
            }
            return false;
        });
    }

    private static Direction findFacing(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (!"facing".equalsIgnoreCase(property.getName())) {
                continue;
            }
            Comparable<?> value = propertyValue(state, property);
            if (value instanceof Direction direction) {
                return direction;
            }
        }
        return null;
    }

    private static Direction rotateDirection(Direction direction, Object rotation) {
        if (!(rotation instanceof UnaryOperator<?> operator)) {
            return direction;
        }
        try {
            @SuppressWarnings("unchecked")
            UnaryOperator<Vec3> vecOperator = (UnaryOperator<Vec3>) operator;
            Vec3 input = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
            Vec3 output = vecOperator.apply(input);
            if (output != null && output.lengthSqr() > 1.0e-7) {
                return nearestDirection(output);
            }
        } catch (RuntimeException ignored) {
            // Fall through to the unrotated actor direction.
        }
        return direction;
    }

    private static Direction nearestDirection(Vec3 vector) {
        Direction best = Direction.NORTH;
        double bestDot = -Double.MAX_VALUE;
        for (Direction candidate : Direction.values()) {
            double dot = vector.x * candidate.getStepX()
                    + vector.y * candidate.getStepY()
                    + vector.z * candidate.getStepZ();
            if (dot > bestDot) {
                bestDot = dot;
                best = candidate;
            }
        }
        return best;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Comparable<?> propertyValue(BlockState state, Property<?> property) {
        return (Comparable<?>) state.getValue((Property) property);
    }

    private static void markBoundaryBlocked(MovementContextView view, BlockPos target) {
        if (view.data == null) {
            return;
        }
        view.data.putBoolean(BLOCKED_KEY, true);
        view.data.putInt(BLOCKED_X, target.getX());
        view.data.putInt(BLOCKED_Y, target.getY());
        view.data.putInt(BLOCKED_Z, target.getZ());
    }

    private static void clearBoundaryMarker(MovementContextView view) {
        if (view.data == null) {
            return;
        }
        view.data.remove(BLOCKED_KEY);
        view.data.remove(BLOCKED_X);
        view.data.remove(BLOCKED_Y);
        view.data.remove(BLOCKED_Z);
    }

    private static void setStall(MovementContextView view, boolean stalled) {
        if (view.fields.stall == MISSING_FIELD) {
            return;
        }
        try {
            view.fields.stall.setBoolean(view.instance, stalled);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // A failed optional compat write must never take down the server.
        }
    }

    private record MovementContextFields(
            Field world,
            Field state,
            Field data,
            Field blockEntityData,
            Field stall,
            Field rotation,
            Field relativeMotion,
            Field contraption) {
        private boolean usable() {
            return world != MISSING_FIELD && state != MISSING_FIELD && data != MISSING_FIELD;
        }
    }

    private record MovementContextView(
            Object instance,
            MovementContextFields fields,
            Level level,
            BlockState state,
            CompoundTag data,
            CompoundTag blockEntityData,
            Object rotation,
            Object relativeMotion,
            Object contraption) {
    }

    private record AutomationBreakOrigin(Level level, BlockPos originSource, BlockPos localSource) {
    }

    @SuppressWarnings("unused")
    private static final class MissingFieldSentinel {
        private static Object missing;
    }

    private static final class MissingMethodSentinel {
        @SuppressWarnings("unused")
        private static void missing() {
        }
    }
}
