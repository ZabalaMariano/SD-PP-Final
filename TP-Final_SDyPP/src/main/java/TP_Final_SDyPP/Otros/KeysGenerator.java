package TP_Final_SDyPP.Otros;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class KeysGenerator {
	
	public PrivateKey privateKey;
    public PublicKey publicKey;
	
	public KeysGenerator() {}	

	//Clave Pública y Privada
	public void generarLlavesAsimetricas() throws NoSuchAlgorithmException{	
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");

        // Inicializar KeyPairGenerator.
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        keyGen.initialize(2048, random);

        // Generar llaves, private key y public key.
        KeyPair keyPair = keyGen.generateKeyPair();
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
	}
	
	public byte[] encriptarAsimetrico(byte[] datosAEncriptar, PublicKey publicKey) {
		byte[] datosEncriptados = null;
		Cipher cipher;
		try {
			cipher = Cipher.getInstance("RSA");
			cipher.init(Cipher.ENCRYPT_MODE, publicKey);
			datosEncriptados = cipher.doFinal(datosAEncriptar);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
			e.printStackTrace();
		}
		return datosEncriptados;
	}
	
	public byte[] desencriptarAsimetrico(byte[] datosEncriptados, PrivateKey privateKey) {
		byte[] datosDesencriptados = null;
		Cipher cipher;
		try {
			cipher = Cipher.getInstance("RSA");
			cipher.init(Cipher.DECRYPT_MODE, privateKey);
			datosDesencriptados = cipher.doFinal(datosEncriptados);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
			e.printStackTrace();
		}
		return datosDesencriptados;
	}
	
	//Clave simétrica
	public SecretKey generarLlaveSimetrica() throws NoSuchAlgorithmException {
		KeyGenerator kgen = KeyGenerator.getInstance("Blowfish");
		SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
		kgen.init(64, sr); //Elijo bits key
		SecretKey key = kgen.generateKey();
		return key;
	}

	public byte[] encriptarSimetrico(SecretKey key, byte[] datosAEncriptar) 
			throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, 
				   IllegalBlockSizeException, BadPaddingException {
		
		Cipher cipher = Cipher.getInstance("Blowfish");
		cipher.init(cipher.ENCRYPT_MODE, key);
		byte[] encrypted = cipher.doFinal(datosAEncriptar);
		return encrypted;
	}
	
	public byte[] desencriptarSimetrico(SecretKey key, byte[] datosEncriptados) 
			throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, 
			IllegalBlockSizeException, BadPaddingException {
		
		Cipher cipher = Cipher.getInstance("Blowfish");
		cipher.init(cipher.DECRYPT_MODE, key);
		byte[] decrypted = cipher.doFinal(datosEncriptados);
		return decrypted;
	}
}
