package com.rikmuld.camping.features.inventory_camping

import com.rikmuld.camping.CampingMod
import com.rikmuld.camping.Library.NBTInfo
import com.rikmuld.camping.features.blocks.lantern.TileEntityLantern
import com.rikmuld.corerm.network.PacketSender
import net.minecraft.entity.player.{EntityPlayer, EntityPlayerMP}
import net.minecraft.item.ItemStack
import net.minecraftforge.event.entity.player.PlayerDropsEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.{PlayerLoggedInEvent, PlayerRespawnEvent}
import net.minecraftforge.fml.common.gameevent.TickEvent.{Phase, PlayerTickEvent}


@Mod.EventBusSubscriber
object EventsServer {

  var tickLight: Int =
    0

  @SubscribeEvent
  def onPlayerDeath(event: PlayerDropsEvent) {
    if (!event.getEntity.world.getGameRules.getBoolean("keepInventory")){
      InventoryCamping.dropItems(event.getEntityPlayer)
      event.getEntity.getEntityData.removeTag(NBTInfo.INV_CAMPING)
    } else {
      val tag = event.getEntity.getEntityData.getCompoundTag(NBTInfo.INV_CAMPING)
      val store = event.getEntity.getEntityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)

      store.setTag(NBTInfo.INV_CAMPING, tag)
      event.getEntity.getEntityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, store)
      event.getEntity.getEntityData.removeTag(NBTInfo.INV_CAMPING)
    }
  }

  @SubscribeEvent
  def onPlayerClone(event: PlayerEvent.Clone) {
    if (event.isWasDeath) {
      val oldPlayer = event.getOriginal
      val newPlayer = event.getEntityPlayer
      // ★★★ 只有开启死亡不掉落时才恢复数据 ★★★
      if (oldPlayer.world.getGameRules.getBoolean("keepInventory")) {
        if (oldPlayer.getEntityData.hasKey(NBTInfo.INV_CAMPING)) {
          val tag = oldPlayer.getEntityData.getCompoundTag(NBTInfo.INV_CAMPING)
          newPlayer.getEntityData.setTag(NBTInfo.INV_CAMPING, tag)
        }
      }
    }
  }

  @SubscribeEvent
  def onPlayerRespawn(event: PlayerRespawnEvent):Unit = {
    val store = event.player.getEntityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
    val tag = store.getCompoundTag(NBTInfo.INV_CAMPING)

    event.player.getEntityData.setTag(NBTInfo.INV_CAMPING, tag)
    if (!tag.hasNoTags) {
      event.player.getEntityData.setTag(NBTInfo.INV_CAMPING, tag)
    }
  }

  @SubscribeEvent
  def onPlayerLogin(event: PlayerLoggedInEvent): Unit = {
    PacketSender.sendToPlayer(new PacketNBTPlayer(
      event.player.getEntityData.getCompoundTag(NBTInfo.INV_CAMPING)
    ), event.player.asInstanceOf[EntityPlayerMP])

    val inv = InventoryCamping.getInventory(event.player)
    val map = inv(InventoryCamping.SLOT_MAP.toByte)
    val lantern = inv(InventoryCamping.SLOT_LANTERN.toByte)

    InventoryCamping.setLanternTime(event.player, TileEntityLantern.timeFromStack(lantern))

    val theMap =
      if(map.isEmpty) None
      else Some(map)

    mapChanged(theMap, event.player)
  }

  @SubscribeEvent
  def onPlayerTick(event: PlayerTickEvent) {
    val player = event.player
    val world = player.world

    if (event.phase.equals(Phase.END) && !world.isRemote) {
      if(InventoryCamping.getLanternTime(player) > 0) {
        val pos = player.getPosition

        Vector(pos, pos.down, pos.up, pos.north, pos.south, pos.west, pos.east).find(pos => {
          if (world.getBlockState(pos).getBlock == CampingMod.OBJ.light) {
            world.getTileEntity(pos).asInstanceOf[TileEntityLight].tick = 0
            true
          }
          else if (world.isAirBlock(pos))
            world.setBlockState(pos, CampingMod.OBJ.light.getDefaultState)
          else
            false
        })

        tickLight += 1
        if (tickLight >= 20) {
          tickLight = 0
          InventoryCamping.lanternTick(player)
        }
      }
    }
  }


  def mapChanged(map: Option[ItemStack], player: EntityPlayer): Unit =
    if(!player.world.isRemote) {
      val packet =
        map.fold(
          new PacketMapData(0, 0, 0, Array())
        )(map => {
          val data = InventoryCamping.getMapData(map, player.world)
          new PacketMapData(data.scale, data.xCenter, data.zCenter, data.colors)
        })

      PacketSender.sendToPlayer(packet, player.asInstanceOf[EntityPlayerMP])
    }
}