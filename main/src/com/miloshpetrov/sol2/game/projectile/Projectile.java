package com.miloshpetrov.sol2.game.projectile;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.ContactImpulse;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.miloshpetrov.sol2.common.Col;
import com.miloshpetrov.sol2.common.SolMath;
import com.miloshpetrov.sol2.game.*;
import com.miloshpetrov.sol2.game.dra.*;
import com.miloshpetrov.sol2.game.item.Shield;
import com.miloshpetrov.sol2.game.particle.*;
import com.miloshpetrov.sol2.game.ship.SolShip;
import com.miloshpetrov.sol2.game.sound.SolSound;

import java.util.ArrayList;
import java.util.List;

public class Projectile implements SolObj {

  private static final float MIN_ANGLE_TO_GUIDE = 2f;
  private final ArrayList<Dra> myDras;
  private final ProjectileBody myBody;
  private final Fraction myFraction;
  private final ParticleSrc myBodyEffect;
  private final ParticleSrc myTrailEffect;
  private final LightSrc myLightSrc;
  private final ProjectileConfig myConfig;

  private boolean myShouldRemove;
  private SolObj myObstacle;

  public Projectile(SolGame game, float angle, Vector2 muzzlePos, Vector2 gunSpd, Fraction fraction,
    ProjectileConfig config, boolean varySpd)
  {
    myDras = new ArrayList<Dra>();
    myConfig = config;

    Dra dra;
    if (myConfig.stretch) {
      dra = new MyDra(this, myConfig.tex, myConfig.texSz);
    } else {
      dra = new RectSprite(myConfig.tex, myConfig.texSz, myConfig.origin.x, myConfig.origin.y, new Vector2(), DraLevel.PROJECTILES, 0, 0, Col.W, false);
    }
    myDras.add(dra);
    float spdLen = myConfig.spdLen;
    if (varySpd) spdLen *= SolMath.rnd(.9f, 1.1f);
    if (myConfig.physSize > 0) {
      myBody = new BallProjectileBody(game, muzzlePos, angle, this, gunSpd, spdLen, myConfig);
    } else {
      myBody = new PointProjectileBody(angle, muzzlePos, gunSpd, spdLen, this, game, myConfig.acc);
    }
    myFraction = fraction;
    myBodyEffect = buildEffect(game, myConfig.bodyEffect, DraLevel.PART_BG_0, null, true);
    myTrailEffect = buildEffect(game, myConfig.trailEffect, DraLevel.PART_BG_0, null, false);
    if (myConfig.lightSz > 0) {
      Color col = Col.W;
      if (myBodyEffect != null) col = myConfig.bodyEffect.tint;
      myLightSrc = new LightSrc(game, myConfig.lightSz, true, 1f, new Vector2(), col);
      myLightSrc.collectDras(myDras);
    } else {
      myLightSrc = null;
    }
  }

  private ParticleSrc buildEffect(SolGame game, EffectConfig ec, DraLevel draLevel, Vector2 pos, boolean inheritsSpd) {
    if (ec == null) return null;
    ParticleSrc res = new ParticleSrc(ec, -1, draLevel, new Vector2(), inheritsSpd, game, pos, myBody.getSpd(), 0);
    if (res.isContinuous()) {
      res.setWorking(true);
      myDras.add(res);
    } else {
      game.getPartMan().finish(game, res, pos);
    }
    return res;
  }

  @Override
  public void update(SolGame game) {
    myBody.update(game);
    if (myObstacle != null) {
      collided(game);
      myObstacle.receiveDmg(myConfig.dmg, game, myBody.getPos(), myConfig.dmgType);
      if (myConfig.emTime > 0 && myObstacle instanceof SolShip) ((SolShip) myObstacle).disableControls(myConfig.emTime, game);
      return;
    }
    if (myLightSrc != null) myLightSrc.update(true, myBody.getAngle(), game);
    maybeGuide(game);
    SolSound ws = myConfig.workSound;
    game.getSoundMan().play(game, ws, null, this);
  }

  private void maybeGuide(SolGame game) {
    if (myConfig.guideRotSpd == 0) return;
    SolShip ne = game.getFractionMan().getNearestEnemy(game, this);
    if (ne == null) return;
    Vector2 nePos = ne.getPos();
    float desiredAngle = myBody.getDesiredAngle(nePos);
    float angle = getAngle();
    float diffAngle = SolMath.norm(desiredAngle - angle);
    if (SolMath.abs(diffAngle) < MIN_ANGLE_TO_GUIDE) return;
    float rot = game.getTimeStep() * myConfig.guideRotSpd;
    diffAngle = SolMath.clamp(diffAngle, -rot, rot);
    myBody.changeAngle(diffAngle);
  }

  private void collided(SolGame game) {
    myShouldRemove = true;
    Vector2 pos = myBody.getPos();
    buildEffect(game, myConfig.collisionEffect, DraLevel.PART_FG_1, pos, false);
    buildEffect(game, myConfig.collisionEffectBg, DraLevel.PART_FG_0, pos, false);
    if (myConfig.collisionEffectBg != null) {
      game.getPartMan().blinks(pos, game, myConfig.collisionEffectBg.sz);
    }
    game.getSoundMan().play(game, myConfig.collisionSound, null, this);
  }

  @Override
  public boolean shouldBeRemoved(SolGame game) {
    return myShouldRemove;
  }

  @Override
  public void onRemove(SolGame game) {
    Vector2 pos = myBody.getPos();
    if (myBodyEffect != null) game.getPartMan().finish(game, myBodyEffect, pos);
    if (myTrailEffect != null) game.getPartMan().finish(game, myTrailEffect, pos);
    myBody.onRemove(game);
  }

  @Override
  public void receiveDmg(float dmg, SolGame game, Vector2 pos, DmgType dmgType) {
    if (myConfig.density > 0) return;
    collided(game);
  }

  @Override
  public boolean receivesGravity() {
    return !myConfig.massless;
  }

  @Override
  public void receiveForce(Vector2 force, SolGame game, boolean acc) {
    myBody.receiveForce(force, game, acc);
  }

  @Override
  public Vector2 getPos() {
    return myBody.getPos();
  }

  @Override
  public FarObj toFarObj() {
    return null;
  }

  @Override
  public List<Dra> getDras() {
    return myDras;
  }

  @Override
  public float getAngle() {
    return myBody.getAngle();
  }

  @Override
  public Vector2 getSpd() {
    return myBody.getSpd();
  }

  @Override
  public void handleContact(SolObj other, ContactImpulse impulse, boolean isA, float absImpulse,
    SolGame game, Vector2 collPos)
  {
  }

  @Override
  public String toDebugString() {
    return null;
  }

  @Override
  public Boolean isMetal() {
    return null;
  }

  @Override
  public boolean hasBody() {
    return true;
  }

  public Fraction getFraction() {
    return myFraction;
  }

  public boolean shouldCollide(SolObj o, Fixture f, FractionMan fractionMan) {
    if (o instanceof SolShip) {
      SolShip s = (SolShip) o;
      if (!fractionMan.areEnemies(s.getPilot().getFraction(), myFraction)) return false;
      if (s.getHull().getShieldFixture() == f) {
        if (myConfig.density > 0) return false;
        Shield shield = s.getShield();
        if (shield == null || shield.getLife() <= 0) return false;
      }
      return true;
    }
    if (o instanceof Projectile) {
      if (!fractionMan.areEnemies(((Projectile) o).myFraction, myFraction)) return false;
    }
    return true;
  }

  public void setObstacle(SolObj o, SolGame game) {
    if (myConfig.density > 0) return;
    if (o instanceof SolShip) {
      Fraction f = ((SolShip) o).getPilot().getFraction();
      // happens for some strange reason : /
      if (!game.getFractionMan().areEnemies(f, myFraction)) return;
    }
    myObstacle = o;
  }

  public boolean isMassless() {
    return myConfig.massless;
  }

  public ProjectileConfig getConfig() {
    return myConfig;
  }


  private static class MyDra implements Dra {
    private final Projectile myProjectile;
    private final TextureAtlas.AtlasRegion myTex;
    private final float myWidth;

    public MyDra(Projectile projectile, TextureAtlas.AtlasRegion tex, float width) {
      myProjectile = projectile;
      myTex = tex;
      myWidth = width;
    }

    @Override
    public Texture getTex0() {
      return myTex.getTexture();
    }

    @Override
    public TextureAtlas.AtlasRegion getTex() {
      return myTex;
    }

    @Override
    public DraLevel getLevel() {
      return DraLevel.PROJECTILES;
    }

    @Override
    public void update(SolGame game, SolObj o) {
    }

    @Override
    public void prepare(SolObj o) {
    }

    @Override
    public Vector2 getPos() {
      return myProjectile.getPos();
    }

    @Override
    public Vector2 getRelPos() {
      return Vector2.Zero;
    }

    @Override
    public float getRadius() {
      return myProjectile.myConfig.texSz/2;
    }

    @Override
    public void draw(GameDrawer drawer, SolGame game) {
      float h = myWidth;
      float minH = game.getCam().getRealLineWidth();
      if (h < minH) h = minH;
      Vector2 pos = myProjectile.getPos();
      float w = myProjectile.getSpd().len() * game.getTimeStep();
      if (w < h) w = h;
      drawer.draw(myTex, w, h, w, h / 2, pos.x, pos.y, SolMath.angle(myProjectile.getSpd()), Col.LG);
    }

    @Override
    public boolean isEnabled() {
      return true;
    }

    @Override
    public boolean okToRemove() {
      return false;
    }

  }

}
