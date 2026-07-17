package expo.modules.libghostty

import expo.modules.libghostty.StickyModifier.Activation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickyModifierTest {
  private var clock = 0L
  private val modifier = StickyModifier { clock }

  @Test
  fun `tap arms an inactive modifier`() {
    modifier.tap()
    assertEquals(Activation.ARMED, modifier.activation)
    assertTrue(modifier.engaged)
    assertFalse(modifier.locked)
  }

  @Test
  fun `consume unlatches an armed modifier`() {
    modifier.tap()
    modifier.consume()
    assertEquals(Activation.INACTIVE, modifier.activation)
    assertFalse(modifier.engaged)
  }

  @Test
  fun `consume keeps an inactive modifier inactive`() {
    modifier.consume()
    assertEquals(Activation.INACTIVE, modifier.activation)
  }

  @Test
  fun `double tap within the window locks`() {
    modifier.tap()
    clock += StickyModifier.DOUBLE_TAP_MS - 1
    modifier.tap()
    assertEquals(Activation.LOCKED, modifier.activation)
    assertTrue(modifier.locked)
  }

  @Test
  fun `slow second tap deactivates instead of locking`() {
    modifier.tap()
    clock += StickyModifier.DOUBLE_TAP_MS
    modifier.tap()
    assertEquals(Activation.INACTIVE, modifier.activation)
  }

  @Test
  fun `consume does not release a locked modifier`() {
    modifier.tap()
    clock += 1
    modifier.tap()
    modifier.consume()
    assertEquals(Activation.LOCKED, modifier.activation)
  }

  @Test
  fun `tap releases a locked modifier`() {
    modifier.tap()
    clock += 1
    modifier.tap()
    clock += 1
    modifier.tap()
    assertEquals(Activation.INACTIVE, modifier.activation)
  }

  @Test
  fun `quick re-tap after consumption arms instead of locking`() {
    modifier.tap()
    modifier.consume()
    clock += 1
    modifier.tap()
    assertEquals(Activation.ARMED, modifier.activation)
  }

  @Test
  fun `arming again after a lock cycle starts from armed`() {
    modifier.tap() // armed
    clock += 1
    modifier.tap() // locked
    clock += 1
    modifier.tap() // inactive
    clock += StickyModifier.DOUBLE_TAP_MS + 1
    modifier.tap()
    assertEquals(Activation.ARMED, modifier.activation)
  }
}
