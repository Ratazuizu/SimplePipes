package alexiil.mc.mod.pipes.part;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import alexiil.mc.mod.pipes.client.model.part.FacadePartKey;
import alexiil.mc.mod.pipes.items.SimplePipeItems;
import alexiil.mc.mod.pipes.part.FacadeShape.Sided;

import alexiil.mc.lib.net.IMsgReadCtx;
import alexiil.mc.lib.net.IMsgWriteCtx;
import alexiil.mc.lib.net.InvalidInputDataException;
import alexiil.mc.lib.net.NetByteBuf;

import alexiil.mc.lib.multipart.api.AbstractPart;
import alexiil.mc.lib.multipart.api.MultipartEventBus;
import alexiil.mc.lib.multipart.api.MultipartHolder;
import alexiil.mc.lib.multipart.api.PartDefinition;
import alexiil.mc.lib.multipart.api.event.PartTransformEvent;
import alexiil.mc.lib.multipart.api.render.PartModelKey;

public class FacadePart extends AbstractPart {

    public final FacadeBlockStateInfo state;
    private FacadeShape shape;

    public FacadePart(
        PartDefinition definition, MultipartHolder holder, FacadeBlockStateInfo states, FacadeShape shape
    ) {
        super(definition, holder);
        this.state = states;
        this.shape = shape;
    }

    public FacadePart(PartDefinition definition, MultipartHolder holder, NbtCompound tag) {
        super(definition, holder);
        this.state = FacadeBlockStateInfo.fromTag(tag.getCompound("states"));
        shape = FacadeShape.fromTag(tag.getCompound("shape"));
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = super.toTag();
        tag.put("states", state.toTag());
        tag.put("shape", shape.toTag());
        return tag;
    }

    public FacadePart(PartDefinition definition, MultipartHolder holder, NetByteBuf buffer, IMsgReadCtx ctx)
        throws InvalidInputDataException {
        super(definition, holder);
        this.state = FacadeBlockStateInfo.readFromBuffer(buffer);
        shape = FacadeShape.fromBuffer(buffer);
    }

    @Override
    public void writeCreationData(NetByteBuf buffer, IMsgWriteCtx ctx) {
        super.writeCreationData(buffer, ctx);
        state.writeToBuffer(buffer);
        shape.toBuffer(buffer);
    }

    @Override
    public void readRenderData(NetByteBuf buffer, IMsgReadCtx ctx) throws InvalidInputDataException {
        super.readRenderData(buffer, ctx);
        shape = FacadeShape.fromBuffer(buffer);
    }

    @Override
    public void writeRenderData(NetByteBuf buffer, IMsgWriteCtx ctx) {
        super.writeRenderData(buffer, ctx);
        shape.toBuffer(buffer);
    }

    @Override
    public void onAdded(MultipartEventBus bus) {
        super.onAdded(bus);

        // Render data is automatically re-sent and parts are re-rendered and re-shaped after transformations are
        // complete.
        bus.addListener(this, PartTransformEvent.class, event -> shape = shape.transform(event.transformation));
    }

    @Override
    public VoxelShape getShape() {
        return shape.shape;
    }

    @Override
    public VoxelShape getCullingShape() {
        return state.state.isOpaque() ? getShape() : VoxelShapes.empty();
    }

    @Override
    public ItemStack getPickStack() {
        return SimplePipeItems.FACADE.createItemStack(new FullFacade(state, shape));
    }

    @Override
    protected BlockState getClosestBlockState() {
        return state.state;
    }

    @Override
    public boolean canOverlapWith(AbstractPart other) {
        // The "no fully overlapping" rule of PartContainer allows us to do this
        if (other instanceof FacadePart) {
            FacadePart o = (FacadePart) other;
            return this.shape.getSize() != FacadeSize.SLAB || o.shape.getSize() != FacadeSize.SLAB;
        }
        return false;
    }

    @Override
    public PartModelKey getModelKey() {
        int insetSides;
        if (shape instanceof Sided sided) {
            insetSides = container.getParts(FacadePart.class, (part) -> part.shape instanceof Sided).stream()
                .reduce(0,
                    (faces, part) -> part.shape instanceof Sided s ? (faces | (1 << s.side.getId())) : faces,
                    (a, b) -> (a | b)
                ) & ~(1 << sided.side.getId());
        } else {
            insetSides = 0;
        }
        return new FacadePartKey(shape, state.state, insetSides);
    }
}
