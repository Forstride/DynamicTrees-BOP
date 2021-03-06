package dynamictreesbop.blocks;

import java.util.ArrayList;
import java.util.Random;

import com.ferreusveritas.dynamictrees.api.treedata.ILeavesProperties;
import com.ferreusveritas.dynamictrees.blocks.BlockDynamicLeaves;
import com.ferreusveritas.dynamictrees.blocks.LeavesProperties;
import com.ferreusveritas.dynamictrees.util.SafeChunkBounds;

import biomesoplenty.api.enums.BOPTrees;
import biomesoplenty.common.block.BlockBOPLeaves;
import dynamictreesbop.DynamicTreesBOP;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockDynamicLeavesFlowering extends BlockDynamicLeaves {
	
	public static final PropertyBool FLOWERING = PropertyBool.create("flowering");
	public static final PropertyBool CAN_FLOWER = PropertyBool.create("can_flower");
	
	private ILeavesProperties properties = LeavesProperties.NULLPROPERTIES;
	
	public BlockDynamicLeavesFlowering() {
		setDefaultState(this.blockState.getBaseState().withProperty(HYDRO, 4).withProperty(FLOWERING, false).withProperty(CAN_FLOWER, false));
		setRegistryName(DynamicTreesBOP.MODID, "leaves_flowering");
		setUnlocalizedName("leaves_flowering");
	}
	
	@Override
	protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, new IProperty[] {HYDRO, FLOWERING, CAN_FLOWER, TREE}); // TREE is unused, but it has to be there to prevent a crash when the constructor of BlockDynamicLeaves sets the default state
	}
	
	@Override
	public IBlockState getStateFromMeta(int meta) {
		return this.getDefaultState().withProperty(FLOWERING, ((meta >> 2) & 1) > 0).withProperty(CAN_FLOWER, ((meta >> 3) & 1) > 0).withProperty(HYDRO, (meta & 3) + 1);
	}
	
	@Override
	public int getMetaFromState(IBlockState state) {
		return (state.getValue(HYDRO) - 1) | (state.getValue(FLOWERING) ? 4 : 0) | (state.getValue(CAN_FLOWER) ? 8 : 0);
	}
	
	public void setProperties(int tree, ILeavesProperties properties) {
		this.properties = properties;
		for (int i = 0; i < 4; i++) super.setProperties(i, properties);
	}
	
	@Override
	public ILeavesProperties getProperties(IBlockState blockState) {
		return properties;
	}
	
	@Override
	public int age(World world, BlockPos pos, IBlockState state, Random rand, SafeChunkBounds safeBounds) {
		ILeavesProperties leavesProperties = getProperties(state);
		int oldHydro = state.getValue(HYDRO);
		
		boolean worldGen = safeBounds != SafeChunkBounds.ANY;
		
		// Check hydration level.  Dry leaves are dead leaves.
		int newHydro = getHydrationLevelFromNeighbors(world, pos, leavesProperties);
		if (newHydro == 0 || (!worldGen && !hasAdequateLight(state, world, leavesProperties, pos))) { // Light doesn't work right during worldgen so we'll just disable it during worldgen for now.
			world.setBlockToAir(pos); // No water, no light .. no leaves
			return -1; // Leaves were destroyed
		} else { 
			// Encode new hydration level in metadata for this leaf
			if (oldHydro != newHydro) { // A little performance gain
				//System.out.println(state.withProperty(HYDRO, MathHelper.clamp(newHydro, 1, 4)));
				world.setBlockState(pos, state.withProperty(HYDRO, MathHelper.clamp(newHydro, 1, 4)), 4);
			}
		}
		
		// If this block can flower, check if flowers should grow or decay
		if (canFlower(state)) {
			boolean flowering = worldGen || world.getLight(pos) >= 14;
			if (isFlowering(state) != flowering) {
				setFlowering(world, pos, flowering, state.withProperty(HYDRO, MathHelper.clamp(newHydro, 1, 4)));
			}
		}
		
		// We should do this even if the hydro is only 1.  Since there could be adjacent branch blocks that could use a leaves block
		for (EnumFacing dir : EnumFacing.VALUES) { // Go on all 6 sides of this block
			if (newHydro > 1 || rand.nextInt(4) == 0 ) { // we'll give it a 1 in 4 chance to grow leaves if hydro is low to help performance
				BlockPos offpos = pos.offset(dir);
				if (safeBounds.inBounds(offpos, true) && isLocationSuitableForNewLeaves(world, leavesProperties, offpos)) { // Attempt to grow new leaves
					int hydro = getHydrationLevelFromNeighbors(world, offpos, leavesProperties);
					if (hydro > 0) {
						boolean canFlower = world.rand.nextInt(4) == 0;
						world.setBlockState(offpos, leavesProperties.getDynamicLeavesState(hydro).withProperty(CAN_FLOWER, canFlower).withProperty(FLOWERING, canFlower && world.getLight(pos) >= 14), 2);
					}
				}
			}
		}
		
		return newHydro; // Leaves were not destroyed
	}
	
	@Override
	public boolean growLeavesIfLocationIsSuitable(World world, ILeavesProperties leavesProp, BlockPos pos, int hydro) {
		hydro = hydro == 0 ? leavesProp.getCellKit().getDefaultHydration() : hydro;
		if (isLocationSuitableForNewLeaves(world, leavesProp, pos)) {
			boolean canFlower = world.rand.nextInt(4) == 0;
			IBlockState state = this.getDefaultState().withProperty(CAN_FLOWER, canFlower).withProperty(FLOWERING, canFlower && world.getLight(pos) >= 14).withProperty(HYDRO, hydro);
			world.setBlockState(pos, state, 2 | (leavesProp.appearanceChangesWithHydro() ? 1 : 0)); // Removed Notify Neighbors Flag for performance
			return true;
		}
		return false;
	}
	
	public boolean isFlowering(IBlockState blockState) {
		return blockState.getValue(FLOWERING);
	}
	
	public boolean canFlower(IBlockState blockState) {
		return blockState.getValue(CAN_FLOWER);
	}
	
	public static void setFlowering(World world, BlockPos pos, boolean flowering, IBlockState currentBlockState) {
		world.setBlockState(pos, currentBlockState.withProperty(FLOWERING, flowering), 2);
	}
	
	@Override
	public ArrayList<ItemStack> onSheared(ItemStack item, IBlockAccess blockAccess, BlockPos pos, int fortune) {
		IBlockState state = blockAccess.getBlockState(pos);
		ArrayList<ItemStack> ret = new ArrayList<ItemStack>();
		
		if (state.getBlock() == this) {
			ret.add(isFlowering(state)
					? BlockBOPLeaves.paging.getVariantItem(BOPTrees.FLOWERING)
					: properties.getPrimitiveLeavesItemStack()
			);
		}
		
		return ret;
	}
	
}
